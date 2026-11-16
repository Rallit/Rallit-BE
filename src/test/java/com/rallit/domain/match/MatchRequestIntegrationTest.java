package com.rallit.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.court.entity.Court;
import com.rallit.domain.match.entity.Match;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
@Transactional
class MatchRequestIntegrationTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/match-requests";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Autowired private MockMvc mvc;
	@Autowired private EntityManager entityManager;
	@Autowired private TokenSessionService sessions;
	@Autowired private JdbcTemplate jdbc;
	@MockitoBean private Clock clock;
	@Autowired private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUpClock() {
		when(clock.getZone()).thenReturn(SEOUL);
		at(LocalDateTime.of(2026, 11, 16, 12, 0));
	}

	@Test
	void persistsConditionsAndReopensResultsWhenOnlySimilarMatchesExist() throws Exception {
		var date = LocalDate.now(clock).plusDays(2);
		long similar = recruitInSeodaemun(date);
		String auth = bearer(member());

		var result = mvc.perform(create(auth, date.plusDays(1), date))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("COMMON201"))
				.andExpect(jsonPath("$.result.exactMatchCount").value(0))
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andExpect(jsonPath("$.result.request.expiresAt").value(date.plusDays(2) + "T00:00:00+09:00"))
				.andReturn();
		long id = requestId(result.getResponse().getContentAsString());
		String location = result.getResponse().getHeader("Location");
		assertThat(location).isEqualTo(PATH + "/" + id);
		entityManager.flush();
		entityManager.clear();

		mvc.perform(get(location).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.requestId").value(id))
				.andExpect(jsonPath("$.result.conditions.dates[0]").value(date.toString()));
		mvc.perform(get(PATH).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.length()").value(1))
				.andExpect(jsonPath("$.result[0].requestId").value(id))
				.andExpect(jsonPath("$.result[0].conditions.dates[0]").value(date.toString()))
				.andExpect(jsonPath("$.result[0].conditions.areaCodes[0]").value("11440"))
				.andExpect(jsonPath("$.result[0].conditions.startTimeTo").value("17:00"));
		mvc.perform(get(PATH + "/{id}/results", id).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(0))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(similar))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("AREA"));
	}

	@Test
	void returnsRequestSpecificOnboardingMessageWithExistingCode() throws Exception {
		var user = User.signUp();
		entityManager.persist(user);
		entityManager.flush();
		String auth = bearer(user);
		var date = LocalDate.now(clock).plusDays(1);

		for (var request : List.of(create(auth, date), delete(PATH + "/42").header("Authorization", auth))) {
			mvc.perform(request)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("MATCH_ONBOARDING_REQUIRED"))
					.andExpect(jsonPath("$.message").value("온보딩을 완료해야 매치 요청 기능을 이용할 수 있습니다."));
		}
	}

	@Test
	void rejectsSavingMixedDoublesTogetherWithSameGenderOnly() throws Exception {
		var date = LocalDate.now(clock).plusDays(1);
		String auth = bearer(member());

		mvc.perform(post(PATH).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("""
				{"dates":["%s"],"areaCodes":["11440"],"startTimeFrom":"14:00","startTimeTo":"17:00",
				 "matchTypes":["SINGLES","MIXED_DOUBLES"],"minNtrp":1.0,"maxNtrp":4.0,"sameGenderOnly":true}
				""".formatted(date)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_REQUEST_INVALID_PREFERENCES"));
		mvc.perform(get(PATH).header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(0));
	}

	@Test
	void hidesAnotherMembersRequestAndRemovesDeletedRequestsFromHome() throws Exception {
		var date = LocalDate.now(clock).plusDays(2);
		String owner = bearer(member());
		String other = bearer(member());
		long id = savedRequest(owner, date);

		mvc.perform(get(PATH).header("Authorization", other))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(0));
		mvc.perform(get(PATH + "/{id}", id).header("Authorization", other))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_REQUEST_NOT_FOUND"));
		mvc.perform(get(PATH + "/{id}/results", id).header("Authorization", other))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_REQUEST_NOT_FOUND"));
		mvc.perform(delete(PATH + "/{id}", id).header("Authorization", other)).andExpect(status().isNotFound());
		mvc.perform(delete(PATH + "/{id}", id).header("Authorization", owner)).andExpect(status().isNoContent());
		mvc.perform(get(PATH).header("Authorization", owner))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(0));
		mvc.perform(get(PATH + "/{id}/results", id).header("Authorization", owner)).andExpect(status().isNotFound());
	}

	@Test
	void limitsActiveRequestsAndFreesSpaceExactlyAtSeoulMidnight() throws Exception {
		var today = LocalDate.now(clock);
		var midnight = today.plusDays(1).atStartOfDay();
		at(midnight.minusSeconds(1));
		String auth = bearer(member());
		long expiring = savedRequest(auth, today);
		savedRequest(auth, today.plusDays(1));
		savedRequest(auth, today.plusDays(2));

		mvc.perform(create(auth, today.plusDays(3)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATCH_REQUEST_LIMIT_REACHED"));
		mvc.perform(get(PATH).header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(3));
		mvc.perform(get(PATH + "/{id}/results", expiring).header("Authorization", auth))
				.andExpect(status().isOk());

		at(midnight);
		mvc.perform(get(PATH).header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(2));
		mvc.perform(get(PATH + "/{id}", expiring).header("Authorization", auth))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_REQUEST_NOT_FOUND"));
		mvc.perform(get(PATH + "/{id}/results", expiring).header("Authorization", auth))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_REQUEST_NOT_FOUND"));
		mvc.perform(create(auth, today.plusDays(3))).andExpect(status().isCreated());
		mvc.perform(get(PATH).header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(3));
	}

	@Test
	void rejectsSavingDatesOutsideTheSupportedWindow() throws Exception {
		var today = LocalDate.now(clock);
		String auth = bearer(member());
		for (var invalid : Set.of(today.minusDays(1), today.plusDays(14))) {
			mvc.perform(create(auth, invalid))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_REQUEST_INVALID_DATE"));
		}
		mvc.perform(create(auth, today.plusDays(13))).andExpect(status().isCreated());
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void allowsOnlyOneConcurrentCreateWhenTwoRequestsAreAlreadyActive() throws Exception {
		var tx = new TransactionTemplate(transactionManager);
		var user = tx.execute(status -> member());
		var date = LocalDate.now(clock).plusDays(2);
		try {
			String auth = bearer(user);
			savedRequest(auth, date);
			savedRequest(auth, date);
			var start = new CyclicBarrier(2);
			Callable<Integer> createRequest = () -> {
				start.await(5, TimeUnit.SECONDS);
				return mvc.perform(create(auth, date)).andReturn().getResponse().getStatus();
			};
			try (var pool = Executors.newFixedThreadPool(2)) {
				var first = pool.submit(createRequest);
				var second = pool.submit(createRequest);
				assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
						.containsExactlyInAnyOrder(201, 409);
			}
			mvc.perform(get(PATH).header("Authorization", auth))
					.andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(3));
		} finally {
			tx.executeWithoutResult(status -> {
				jdbc.update("delete from match_request where owner_id = ?", user.getId());
				entityManager.remove(entityManager.find(User.class, user.getId()));
			});
		}
	}

	private void at(LocalDateTime time) {
		when(clock.instant()).thenReturn(time.atZone(SEOUL).toInstant());
	}

	private long savedRequest(String auth, LocalDate date) throws Exception {
		return requestId(mvc.perform(create(auth, date)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private long requestId(String body) {
		return ((Number) JsonPath.read(body, "$.result.request.requestId")).longValue();
	}

	private MockHttpServletRequestBuilder create(String auth, LocalDate... dates) {
		String dateJson = Arrays.stream(dates).map(date -> "\"" + date + "\"")
				.collect(Collectors.joining(",", "[", "]"));
		return post(PATH).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("""
				{"dates":%s,"areaCodes":["11440"],"startTimeFrom":"14:00","startTimeTo":"17:00",
				 "matchTypes":["SINGLES"],"minNtrp":1.0,"maxNtrp":4.0,"sameGenderOnly":false}
				""".formatted(dateJson));
	}

	private User member() {
		var user = EntityFixtures.user(entityManager, "요청" + UUID.randomUUID().toString().substring(0, 8), Gender.FEMALE);
		entityManager.persist(user);
		entityManager.flush();
		return user;
	}

	private String bearer(User user) {
		return "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
	}

	private long recruitInSeodaemun(LocalDate date) {
		var host = member();
		var area = entityManager.createQuery("select a from Area a where a.code = '11410'", Area.class)
				.getSingleResult();
		var court = Court.builder().area(area).name("가좌테니스장").address("서울 서대문구")
				.latitude(new BigDecimal("37.5824")).longitude(new BigDecimal("126.92985")).build();
		entityManager.persist(court);
		var slot = EntityFixtures.courtSlot(court, host, "1", date.atTime(14, 0));
		entityManager.persist(slot);
		var match = Match.builder().courtSlot(slot).host(host).matchTypes(Set.of(MatchType.SINGLES))
				.maleRecruitCount(1).femaleRecruitCount(1).participationFee(10000)
				.recruitmentDeadline(date.atTime(13, 0)).build();
		entityManager.persist(match);
		entityManager.flush();
		jdbc.update("update court_slot set status = 'APPROVED' where id = ?", slot.getId());
		jdbc.update("update match set status = 'RECRUITING' where id = ?", match.getId());
		entityManager.clear();
		return match.getId();
	}
}
