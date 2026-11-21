package com.rallit.domain.match;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.court.entity.Court;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
@Transactional
class MatchTimeZoneIntegrationTest extends IntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-11-17T15:30:00Z");

	@Autowired private MockMvc mvc;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TokenSessionService sessions;
	@MockitoBean private Clock clock;

	@BeforeEach
	void fixClockAtSeoulMidnightPlusThirtyMinutes() {
		when(clock.instant()).thenReturn(NOW);
	}

	@Test
	void listsMatchesBySeoulDateEvenWhenTheUtcDateDiffers() throws Exception {
		var host = member(Gender.MALE);
		long seoulMorning = recruitingMatch(host, "2026-11-17T23:00:00Z");
		recruitingMatch(host, "2026-11-18T22:00:00Z");
		String auth = bearer(member(Gender.FEMALE));

		mvc.perform(get("/api/v1/matches").param("date", "2026-11-18").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(1))
				.andExpect(jsonPath("$.result.items[0].matchId").value(seoulMorning))
				.andExpect(jsonPath("$.result.items[0].startsAt").value("2026-11-17T23:00:00Z"))
				.andExpect(jsonPath("$.result.items[0].endsAt").value("2026-11-18T01:00:00Z"));
		mvc.perform(get("/api/v1/matches").param("date", "2026-11-18").param("startTimeFrom", "08:00")
						.param("startTimeTo", "10:00").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[0].matchId").value(seoulMorning));
		mvc.perform(get("/api/v1/matches/{matchId}", seoulMorning).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.startsAt").value("2026-11-17T23:00:00Z"))
				.andExpect(jsonPath("$.result.recruitmentDeadline").value("2026-11-17T22:00:00Z"));
	}

	@Test
	void listsScheduledApplicationsFromTheStartOfTheSeoulDay() throws Exception {
		var host = member(Gender.MALE);
		var applicant = member(Gender.FEMALE);
		long yesterdayInSeoul = application(recruitingMatch(host, "2026-11-17T14:00:00Z"), applicant);
		long todayInSeoul = application(recruitingMatch(host, "2026-11-17T23:00:00Z"), applicant);
		long tomorrowInSeoul = application(recruitingMatch(host, "2026-11-18T22:00:00Z"), applicant);
		String auth = bearer(applicant);

		mvc.perform(get("/api/v1/users/me/applications").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(2))
				.andExpect(jsonPath("$.result.items[0].applicationId").value(todayInSeoul))
				.andExpect(jsonPath("$.result.items[1].applicationId").value(tomorrowInSeoul));
		mvc.perform(get("/api/v1/users/me/applications").param("date", "2026-11-17").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(1))
				.andExpect(jsonPath("$.result.items[0].applicationId").value(yesterdayInSeoul))
				.andExpect(jsonPath("$.result.items[0].startsAt").value("2026-11-17T14:00:00Z"));
	}

	@Test
	void recommendsAndSearchesBySeoulWeekdayAndTimeRegardlessOfSessionAndJvmTimeZone() throws Exception {
		var host = member(Gender.MALE);
		long saturdayMorning = recruitingMatch(host, "2026-11-20T23:00:00Z");
		recruitingMatch(host, "2026-11-21T04:00:00Z");
		String auth = bearer(member(Gender.FEMALE));
		var defaultZone = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
			jdbc.execute("SET LOCAL TIME ZONE 'Pacific/Kiritimati'");

			mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", auth))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.result.totalElements").value(1))
					.andExpect(jsonPath("$.result.items[0].match.matchId").value(saturdayMorning))
					.andExpect(jsonPath("$.result.items[0].match.startsAt").value("2026-11-20T23:00:00Z"));
			String created = mvc.perform(post("/api/v1/match-requests").header("Authorization", auth)
							.contentType(MediaType.APPLICATION_JSON).content("""
									{"dates":["2026-11-21"],"areaCodes":["11440"],"startTimeFrom":"07:00",
									 "startTimeTo":"10:00","matchTypes":["SINGLES"],"minNtrp":1.0,"maxNtrp":4.0,
									 "sameGenderOnly":false}
									"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.result.exactMatchCount").value(1))
					.andExpect(jsonPath("$.result.request.expiresAt").value("2026-11-21T15:00:00Z"))
					.andReturn().getResponse().getContentAsString();
			long requestId = ((Number) JsonPath.read(created, "$.result.request.requestId")).longValue();
			mvc.perform(get("/api/v1/match-requests/{id}/results", requestId).header("Authorization", auth))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(saturdayMorning))
					.andExpect(jsonPath("$.result.similarMatches.totalElements").value(0));
		} finally {
			TimeZone.setDefault(defaultZone);
		}
	}

	@Test
	void recordsApplicationTimesFromTheUtcClockAndCapsPaymentDueAtTheFinalDeadline() throws Exception {
		var host = member(Gender.MALE);
		var applicant = member(Gender.FEMALE);
		long soon = recruitingMatch(host, "2026-11-17T23:00:00Z");
		long later = recruitingMatch(host, "2026-11-19T00:00:00Z");
		String hostAuth = bearer(host);
		String applicantAuth = bearer(applicant);

		String applied = mvc.perform(post("/api/v1/matches/{matchId}/applications", soon)
						.header("Authorization", applicantAuth).contentType(MediaType.APPLICATION_JSON)
						.content("{\"refundPolicyAgreed\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result.appliedAt").value("2026-11-17T15:30:00Z"))
				.andReturn().getResponse().getContentAsString();
		long soonApplication = ((Number) JsonPath.read(applied, "$.result.applicationId")).longValue();
		long laterApplication = application(later, applicant);

		mvc.perform(post("/api/v1/host/matches/{matchId}/applications/{id}/accept", soon, soonApplication)
						.header("Authorization", hostAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.acceptedAt").value("2026-11-17T15:30:00Z"))
				.andExpect(jsonPath("$.result.paymentDueAt").value("2026-11-17T22:00:00Z"));
		mvc.perform(post("/api/v1/host/matches/{matchId}/applications/{id}/accept", later, laterApplication)
						.header("Authorization", hostAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.paymentDueAt").value("2026-11-18T15:30:00Z"));
		mvc.perform(get("/api/v1/applications/{applicationId}", soonApplication).header("Authorization", applicantAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.appliedAt").value("2026-11-17T15:30:00Z"))
				.andExpect(jsonPath("$.result.refundPolicyAgreedAt").value("2026-11-17T15:30:00Z"))
				.andExpect(jsonPath("$.result.payment.paymentDueAt").value("2026-11-17T22:00:00Z"));
	}

	private long recruitingMatch(User host, String startsAt) {
		var area = entityManager.createQuery("select a from Area a where a.code = '11440'", Area.class)
				.getSingleResult();
		var court = Court.builder().area(area).name("시간대 테스트 코트").address("서울특별시 마포구")
				.latitude(new BigDecimal("37.560")).longitude(new BigDecimal("126.900")).build();
		entityManager.persist(court);
		var start = Instant.parse(startsAt);
		var slot = EntityFixtures.courtSlot(court, host, "1", start);
		entityManager.persist(slot);
		var match = Match.builder().courtSlot(slot).host(host).matchTypes(Set.of(MatchType.SINGLES))
				.maleRecruitCount(0).femaleRecruitCount(1).participationFee(10000)
				.recruitmentDeadline(start.minus(Duration.ofHours(1))).build();
		entityManager.persist(match);
		entityManager.flush();
		jdbc.update("update court_slot set status = 'APPROVED' where id = ?", slot.getId());
		jdbc.update("update match set status = 'RECRUITING' where id = ?", match.getId());
		entityManager.clear();
		return match.getId();
	}

	private long application(long matchId, User applicant) {
		var application = MatchApplication.create(entityManager.find(Match.class, matchId),
				entityManager.find(User.class, applicant.getId()), NOW);
		entityManager.persist(application);
		entityManager.flush();
		entityManager.clear();
		return application.getId();
	}

	private User member(Gender gender) {
		var user = EntityFixtures.user(entityManager, "시간" + UUID.randomUUID().toString().substring(0, 8), gender);
		entityManager.persist(user);
		entityManager.flush();
		return user;
	}

	private String bearer(User user) {
		return "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
	}
}
