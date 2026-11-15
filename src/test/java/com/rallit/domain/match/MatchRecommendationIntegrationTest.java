package com.rallit.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import java.util.UUID;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.court.entity.Court;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.region.repository.AreaRepository;
import com.rallit.domain.user.entity.OnboardingProfile;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.ExperiencePeriod;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.enums.PreferredTimeSlot;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
@Transactional
class MatchRecommendationIntegrationTest extends IntegrationTestSupport {

	private static final String MAPO = "11440";
	private static final String SEODAEMUN = "11410";

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager entityManager;
	@Autowired private AreaRepository areas;
	@Autowired private TokenSessionService sessions;
	@Autowired private Clock clock;

	@Test
	void filtersHomePreferencesAndRanksNtrpBeforeStartTime() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var viewer = viewer();
		var original = court(MAPO, "37.560", "126.900");
		var outside = court(SEODAEMUN, "37.5824", "126.92985");
		long lower = match(host, original, date, "12:00", "2.5", "3.5", MatchType.WOMENS_DOUBLES, false);
		long higher = match(host, original, date, "14:00", "3.5", "4.0", MatchType.SINGLES, false);
		match(host, original, date, "10:00", "3.5", "4.0", MatchType.SINGLES, false);
		match(host, original, date, "16:00", "3.5", "4.0", MatchType.MIXED_DOUBLES, false);
		match(host, original, date.plusDays(2), "14:00", "3.5", "4.0", MatchType.SINGLES, false);
		match(host, outside, date, "14:00", "3.5", "4.0", MatchType.SINGLES, false);
		match(viewer, original, date, "16:00", "3.5", "4.0", MatchType.SINGLES, false);
		String auth = bearer(viewer);

		mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", auth).param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(2))
				.andExpect(jsonPath("$.result.items[0].match.matchId").value(higher));
		mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", auth).param("size", "1").param("page", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[0].match.matchId").value(lower));
		mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", auth).param("size", "1").param("page", "2"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_PAGE_OUT_OF_RANGE"));
		assertThat(jdbc.queryForObject("select count(*) from areas where center_latitude is not null and center_longitude is not null",
				Long.class)).isEqualTo(25L);
	}

	private User viewer() {
		return member(Gender.FEMALE, "4.0");
	}

	private User member(Gender gender, String ntrp) {
		var area = areas.findAllByCodeIn(Set.of(MAPO)).getFirst();
		var user = User.signUp();
		user.completeOnboarding(new OnboardingProfile("추천" + UUID.randomUUID().toString().substring(0, 8),
				gender, AgeGroup.TWENTIES, ExperiencePeriod.ONE_TO_THREE_YEARS, new BigDecimal(ntrp), false,
				Set.of(PreferredMatchType.SINGLES, PreferredMatchType.DOUBLES), Set.of(area),
				Set.of(PreferredTimeSlot.WEEKEND_AFTERNOON)));
		entityManager.persist(user);
		entityManager.flush();
		return user;
	}

	private Court court(String areaCode, String latitude, String longitude) {
		var area = areas.findAllByCodeIn(Set.of(areaCode)).getFirst();
		var court = Court.builder().area(area).name("테스트코트").address("서울특별시").latitude(new BigDecimal(latitude))
				.longitude(new BigDecimal(longitude)).build();
		entityManager.persist(court);
		return court;
	}

	private long match(User host, Court court, LocalDate date, String start, String min, String max,
			MatchType type, boolean sameGenderOnly) {
		var startsAt = date.atTime(LocalTime.parse(start));
		var slot = EntityFixtures.courtSlot(court, host, UUID.randomUUID().toString().substring(0, 8), startsAt);
		entityManager.persist(slot);
		var match = Match.builder().courtSlot(slot).host(host).matchTypes(Set.of(type))
				.maleRecruitCount(sameGenderOnly ? 0 : 2).femaleRecruitCount(2)
				.sameGenderOnly(sameGenderOnly)
				.minNtrp(new BigDecimal(min)).maxNtrp(new BigDecimal(max))
				.participationFee(10000).recruitmentDeadline(startsAt.minusHours(1)).build();
		entityManager.persist(match);
		entityManager.flush();
		jdbc.update("update court_slot set status = 'APPROVED' where id = ?", slot.getId());
		jdbc.update("update match set status = 'RECRUITING' where id = ?", match.getId());
		return match.getId();
	}

	private LocalDate saturday() {
		return LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
	}

	private String bearer(User user) {
		entityManager.clear();
		return "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
	}
}
