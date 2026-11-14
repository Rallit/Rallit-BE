package com.rallit.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
@Transactional
class MatchDiscoveryIntegrationTest extends IntegrationTestSupport {

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private TokenSessionService sessions;
	@Autowired private UserRepository users;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private Clock clock;

	@Test
	void seedListDetailAndApplicationUseTheSameMatchId() throws Exception {
		String bearer = bearer(activeUser());
		LocalDate date = LocalDate.now(clock).plusDays(7);
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(0));

		seed(date);
		String list = mvc.perform(get("/api/v1/matches").header("Authorization", bearer)
						.param("date", date.toString()).param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("COMMON200"))
				.andExpect(jsonPath("$.result.items", hasSize(1)))
				.andExpect(jsonPath("$.result.items[0].currentParticipantCount").value(1))
				.andExpect(jsonPath("$.result.items[0].startsAt").value(date + "T10:00:00+09:00"))
				.andExpect(jsonPath("$.result.page").value(0))
				.andExpect(jsonPath("$.result.size").value(1))
				.andExpect(jsonPath("$.result.hasNext").value(true))
				.andExpect(jsonPath("$.result.totalElements").value(3))
				.andReturn().getResponse().getContentAsString();
		long matchId = json.readTree(list).path("result").path("items").get(0).path("matchId").asLong();

		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.matchId").value(matchId))
				.andExpect(jsonPath("$.result.endsAt").value(date + "T12:00:00+09:00"))
				.andExpect(jsonPath("$.result.recruitmentDeadline").value(date + "T09:00:00+09:00"))
				.andExpect(jsonPath("$.result.canApply").value(true))
				.andExpect(jsonPath("$.result.court.address").isString())
				.andExpect(jsonPath("$.result.court.evidenceFileKey").doesNotExist());
		var application = mvc.perform(post("/api/v1/matches/{matchId}/applications", matchId)
						.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
						.content("{\"refundPolicyAgreed\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result.matchId").value(matchId))
				.andExpect(jsonPath("$.result.status").value("PENDING"))
				.andReturn().getResponse();

		mvc.perform(get("/api/v1/users/me/applications").header("Authorization", bearer)
						.param("date", date.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[0].matchId").value(matchId))
				.andExpect(jsonPath("$.result.items[0].startsAt").value(date + "T10:00:00+09:00"))
				.andExpect(jsonPath("$.result.items[0].endsAt").value(date + "T12:00:00+09:00"));
		mvc.perform(get(application.getHeader("Location")).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.match.startsAt").value(date + "T10:00:00+09:00"))
				.andExpect(jsonPath("$.result.match.endsAt").value(date + "T12:00:00+09:00"))
				.andExpect(jsonPath("$.result.match.recruitmentDeadline").value(date + "T09:00:00+09:00"));

		seed(date);
		assertThat(jdbc.queryForObject("select count(*) from match_application where match_id = ? and status = 'PENDING'",
				Long.class, matchId)).isEqualTo(1L);
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(3))
				.andExpect(jsonPath("$.result.items[0].matchId").value(matchId))
				.andExpect(jsonPath("$.result.items[0].currentParticipantCount").value(1));
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.canApply").value(false))
				.andExpect(jsonPath("$.result.applicationBlockReason").value("APPLICATION_DUPLICATED"));
	}

	@Test
	void rejectsPagesBeyondFilteredResults() throws Exception {
		String bearer = bearer(activeUser());
		LocalDate date = LocalDate.now(clock).plusDays(7);
		seed(date);

		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("size", "1").param("page", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.hasNext").value(false));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("matchTypes", "SINGLES").param("size", "1").param("page", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(1))
				.andExpect(jsonPath("$.result.hasNext").value(false));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("matchTypes", "SINGLES").param("size", "1").param("page", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_PAGE_OUT_OF_RANGE"));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("matchTypes", "WOMENS_DOUBLES").param("page", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items", hasSize(0)));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("matchTypes", "WOMENS_DOUBLES").param("page", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_PAGE_OUT_OF_RANGE"));
	}

	@Test
	void rejectsBlankMatchTypesAndAcceptsRepeatedValues() throws Exception {
		String bearer = bearer(activeUser());
		LocalDate date = LocalDate.now(clock).plusDays(7);
		seed(date);

		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("matchTypes", "SINGLES,"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("matchTypes", "SINGLES", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("matchTypes", "SINGLES", "SINGLES", "SINGLES", "SINGLES", "SINGLES", "SINGLES"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(1));
	}

	@Test
	void filtersApplicantEligibilityBeforeCountingAndPaging() throws Exception {
		User viewer = activeUser();
		String bearer = bearer(viewer);
		LocalDate date = LocalDate.now(clock).plusDays(7);
		seed(date);

		long singlesId = demoMatchId(date, "singles");
		long rallyId = demoMatchId(date, "rally");
		long doublesId = demoMatchId(date, "mixed-doubles");
		mvc.perform(post("/api/v1/matches/{matchId}/applications", singlesId)
						.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
						.content("{\"refundPolicyAgreed\":true}"))
				.andExpect(status().isCreated());
		jdbc.update("update match set host_user_id = ? where id = ?", viewer.getId(), rallyId);
		jdbc.update("update match set female_recruit_count = 1 where id = ?", doublesId);
		jdbc.update("""
				insert into match_application (match_id, applicant_user_id, applicant_gender, status,
					refund_policy_agreed_at, created_at, updated_at)
				values (?, ?, 'FEMALE', 'PAYMENT_PENDING', localtimestamp, localtimestamp, localtimestamp)
				""", doublesId, activeUser().getId());

		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("size", "1").param("applicableOnly", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(3))
				.andExpect(jsonPath("$.result.items[0].matchId").value(singlesId));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("size", "1").param("applicableOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(1))
				.andExpect(jsonPath("$.result.hasNext").value(false))
				.andExpect(jsonPath("$.result.items[0].matchId").value(doublesId));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("size", "1").param("page", "1").param("applicableOnly", "true"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_PAGE_OUT_OF_RANGE"));

		jdbc.update("update match_application set status = 'CONFIRMED' where match_id = ?", doublesId);
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("applicableOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(0))
				.andExpect(jsonPath("$.result.items", hasSize(0)));
	}

	@Test
	void applicableOnlyChecksTheViewersRecruitmentGenderAndKeepsMatchTypeFilter() throws Exception {
		String bearer = bearer(users.saveAndFlush(EntityFixtures.user(entityManager,
				"탐색" + UUID.randomUUID().toString().substring(0, 8), Gender.MALE)));
		LocalDate date = LocalDate.now(clock).plusDays(7);
		seed(date);

		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("matchTypes", "SINGLES", "RALLY").param("applicableOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(1))
				.andExpect(jsonPath("$.result.items[0].matchId").value(demoMatchId(date, "rally")));
	}

	@Test
	void rejectsInvalidCalendarDatesAndYearsOutsideFourDigits() throws Exception {
		String bearer = bearer(activeUser());
		for (String date : new String[]{"+999999999-12-31", "+500000-01-01", "0000-01-01", "2026-02-30"}) {
			mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON400"));
		}

		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", "9999-12-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(0));
	}

	@Test
	void filtersByEntireTimeRangeAndMatchType() throws Exception {
		String bearer = bearer(activeUser());
		LocalDate date = LocalDate.now(clock).plusDays(7);
		seed(date);
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("startTimeFrom", "10:00").param("startTimeTo", "12:00")
						.param("matchTypes", "SINGLES", "RALLY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(1))
				.andExpect(jsonPath("$.result.items[0].matchTypes[0]").value("SINGLES"));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("startTimeFrom", "10:00").param("startTimeTo", "11:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(0));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("startTimeTo", "12:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_DATE_REQUIRED_FOR_TIME_FILTER"));
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString())
						.param("startTimeFrom", "12:00").param("startTimeTo", "11:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_INVALID_TIME_RANGE"));
	}

	@Test
	void checksOnboardingAndHidesUnapprovedMatches() throws Exception {
		mvc.perform(get("/api/v1/matches")).andExpect(status().isUnauthorized());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		User onboarding = User.signUp("대기" + suffix, "pending-" + suffix + "@example.test");
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer(users.saveAndFlush(onboarding))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MATCH_ONBOARDING_REQUIRED"));

		String bearer = bearer(activeUser());
		LocalDate date = LocalDate.now(clock).plusDays(7);
		seed(date);
		long matchId = jdbc.queryForObject("""
				select m.id from match m join court_slot s on s.id = m.court_slot_id
				where s.evidence_file_key = ?
				""", Long.class, "local-demo/21/" + date + "/singles");
		jdbc.update("update court_slot set status = 'PENDING' where id = (select court_slot_id from match where id = ?)",
				matchId);
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(2));
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", bearer))
				.andExpect(status().isNotFound());
	}

	@Test
	void detailExplainsGenderCapacityAndRecruitmentDeadline() throws Exception {
		User viewer = activeUser();
		String bearer = bearer(viewer);
		LocalDate date = LocalDate.now(clock).plusDays(7);
		seed(date);
		long matchId = jdbc.queryForObject("""
				select m.id from match m join court_slot s on s.id = m.court_slot_id
				where s.evidence_file_key = ?
				""", Long.class, "local-demo/21/" + date + "/singles");
		User confirmed = activeUser();
		jdbc.update("""
				insert into match_application (match_id, applicant_user_id, applicant_gender, status,
					refund_policy_agreed_at, created_at, updated_at)
				values (?, ?, 'FEMALE', 'CONFIRMED', localtimestamp, localtimestamp, localtimestamp)
				""", matchId, confirmed.getId());
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.currentParticipantCount").value(2))
				.andExpect(jsonPath("$.result.applicationBlockReason").value("RECRUITMENT_FULL"));

		jdbc.update("update match set recruitment_deadline = ? where id = ?", date.minusDays(8).atStartOfDay(),
				matchId);
		entityManager.clear();
		mvc.perform(get("/api/v1/matches").header("Authorization", bearer).param("date", date.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(2));
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.recruitmentOpen").value(false))
				.andExpect(jsonPath("$.result.applicationBlockReason").value("MATCH_RECRUITMENT_CLOSED"));
	}

	private void seed(LocalDate date) throws Exception {
		jdbc.queryForObject("select set_config('rallit.demo_date', ?, true)", String.class, date.toString());
		jdbc.execute(Files.readString(Path.of("scripts/local/seed-matches.sql")));
	}

	private long demoMatchId(LocalDate date, String seedKey) {
		return jdbc.queryForObject("""
				select m.id from match m join court_slot s on s.id = m.court_slot_id
				where s.evidence_file_key = ?
				""", Long.class, "local-demo/21/" + date + "/" + seedKey);
	}

	private User activeUser() {
		return users.saveAndFlush(EntityFixtures.user(entityManager, "탐색" + UUID.randomUUID().toString().substring(0, 8),
				Gender.FEMALE));
	}

	private String bearer(User user) {
		return "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
	}
}
