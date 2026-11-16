package com.rallit.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
@Transactional
class MatchRecommendationIntegrationTest extends IntegrationTestSupport {

	private static final String MAPO = "11440";
	private static final String JUNG = "11140";
	private static final String SEODAEMUN = "11410";
	private static final String YONGSAN = "11170";

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager entityManager;
	@Autowired private AreaRepository areas;
	@Autowired private TokenSessionService sessions;
	@Autowired private Clock clock;

	@Test
	void emptySearchIsAValidResponseForAnOnboardedMember() throws Exception {
		mvc.perform(search(bearer(viewer()), saturday()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(0))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(0))
				.andExpect(jsonPath("$.result.hasResults").value(false));
	}

	@Test
	void returnsExactAndSimilarSectionsAndChecksTheTierBeforePaging() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		var nearby = court(SEODAEMUN, "37.5824", "126.92985");
		var tooFar = court(YONGSAN, "37.522", "126.978");
		long exact = match(host, original, date, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		long similar = match(host, nearby, date, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		match(host, original, date, "13:00", "2.5", "4.0", MatchType.SINGLES, false);
		match(host, tooFar, date, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		String auth = bearer(viewer());

		mvc.perform(search(auth, date))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(exact))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(similar))
				.andExpect(jsonPath("$.result.similarMatches.items[0].areaName").value("서대문구"))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("AREA"));
		for (String pageParam : Set.of("exactPage", "similarPage")) {
			mvc.perform(search(auth, date).param(pageParam, "1").param("size", "1"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("MATCH_PAGE_OUT_OF_RANGE"));
		}
		mvc.perform(get("/api/v1/matches/{matchId}", similar).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.matchId").value(similar));
	}

	@Test
	void appliesOneWindowAndRelaxationTierAcrossOnlyTheSelectedDates() throws Exception {
		var firstDate = saturday();
		var lastDate = firstDate.plusDays(2);
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		var nearby = court(SEODAEMUN, "37.5824", "126.92985");
		long first = match(host, original, firstDate, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		long last = match(host, original, lastDate, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		long similar = match(host, nearby, lastDate, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		match(host, original, firstDate.plusDays(1), "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		match(host, original, firstDate, "13:00", "2.5", "4.0", MatchType.SINGLES, false);
		String auth = bearer(viewer());

		mvc.perform(search(auth, lastDate, firstDate, firstDate).param("size", "1").param("exactPage", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(2))
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(last))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(similar))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("AREA"));
		mvc.perform(search(auth, lastDate, firstDate))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(first));
	}

	@Test
	void findsTimeOnlyThenBothWhilePreservingExplicitFilters() throws Exception {
		var date = saturday();
		var host = member(Gender.FEMALE, "3.0");
		var viewer = viewer();
		var exactArea = court(MAPO, "37.560", "126.900");
		var nearby = court(SEODAEMUN, "37.5824", "126.92985");
		var tooFar = court(YONGSAN, "37.522", "126.978");
		match(host, exactArea, date, "12:59", "2.5", "4.0", MatchType.SINGLES, true);
		long timeOnly = match(host, exactArea, date, "13:00", "2.5", "4.0", MatchType.SINGLES, true);
		long both = match(host, nearby, date, "16:00", "2.5", "4.0", MatchType.SINGLES, true);
		match(host, nearby, date, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		match(host, nearby, date, "14:00", "2.5", "3.5", MatchType.SINGLES, true);
		match(host, nearby, date, "14:00", "4.0", "4.0", MatchType.SINGLES, true);
		match(host, nearby, date, "14:00", "2.5", "4.0", MatchType.RALLY, true);
		match(host, tooFar, date, "14:00", "2.5", "4.0", MatchType.SINGLES, true);
		match(viewer, nearby, date, "14:00", "2.5", "4.0", MatchType.SINGLES, true);
		String auth = bearer(viewer);

		mvc.perform(search(auth, date).param("ntrp", "3.0", "4.0+").param("sameGenderOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(0))
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(timeOnly))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("TIME"));
		jdbc.update("update match set status = 'CANCELED' where id = ?", timeOnly);
		entityManager.clear();
		mvc.perform(search(auth, date).param("ntrp", "3.0", "4.0+").param("sameGenderOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(0))
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(both))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("AREA"))
				.andExpect(jsonPath("$.result.relaxedConditions[1]").value("TIME"));
	}

	@Test
	void comparesAgainstEverySelectedDistrictAndDoesNotRelaxAcrossMidnight() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		var nearJung = court(YONGSAN, "37.53125", "126.98032");
		long similar = match(host, nearJung, date, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		long endsAtMidnight = match(host, original, date, "22:00", "2.5", "4.0", MatchType.RALLY, false);
		match(host, original, date, "22:01", "2.5", "4.0", MatchType.RALLY, false);
		String auth = bearer(viewer());

		mvc.perform(get("/api/v1/matches/search").header("Authorization", auth)
						.param("dates", date.toString()).param("areaCodes", MAPO, JUNG).param("matchTypes", "SINGLES")
						.param("startTimeFrom", "14:00").param("startTimeTo", "17:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(similar))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("AREA"));
		mvc.perform(get("/api/v1/matches/search").header("Authorization", auth)
						.param("dates", date.toString()).param("areaCodes", MAPO).param("startTimeFrom", "23:00")
						.param("startTimeTo", "24:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(endsAtMidnight))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("TIME"));
		mvc.perform(get("/api/v1/matches/search").header("Authorization", auth)
						.param("dates", date.toString()).param("areaCodes", MAPO).param("startTimeFrom", "22:00")
						.param("startTimeTo", "24:00").param("matchTypes", "RALLY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(endsAtMidnight))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(0));
	}

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

	@Test
	void comparesOnlyConfiguredNtrpBoundsWhenSearchSpecifiesLevels() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		var nearby = court(SEODAEMUN, "37.5824", "126.92985");
		long lowerBoundOnly = match(host, original, date, "14:00", "2.5", null, MatchType.SINGLES, false);
		long upperBoundOnly = match(host, nearby, date, "14:00", null, "3.5", MatchType.SINGLES, false);
		long unspecifiedExact = match(host, original, date, "14:00", null, null, MatchType.SINGLES, false);
		long unspecifiedSimilar = match(host, nearby, date, "14:00", null, null, MatchType.SINGLES, false);
		match(host, original, date, "15:00", "3.5", null, MatchType.SINGLES, false);
		match(host, nearby, date, "15:00", null, "3.0", MatchType.SINGLES, false);
		String auth = bearer(viewer());

		mvc.perform(search(auth, date).param("ntrp", "3.0", "3.5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(lowerBoundOnly))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(upperBoundOnly));
		mvc.perform(search(auth, date))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(3))
				.andExpect(jsonPath("$.result.exactMatches.items[1].match.matchId").value(unspecifiedExact))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(3))
				.andExpect(jsonPath("$.result.similarMatches.items[1].match.matchId").value(unspecifiedSimilar));
	}

	@Test
	void ranksMatchingOneSidedNtrpRangesBeforeUnspecifiedAndNonMatchingRanges() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		long unspecified = match(host, original, date, "12:00", null, null, MatchType.SINGLES, false);
		long belowViewer = match(host, original, date, "13:00", null, "3.5", MatchType.SINGLES, false);
		long lowerBoundOnly = match(host, original, date, "14:00", "3.5", null, MatchType.SINGLES, false);
		long upperBoundOnly = match(host, original, date, "15:00", null, "4.0", MatchType.SINGLES, false);

		mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", bearer(viewer())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(4))
				.andExpect(jsonPath("$.result.items[0].match.matchId").value(lowerBoundOnly))
				.andExpect(jsonPath("$.result.items[1].match.matchId").value(upperBoundOnly))
				.andExpect(jsonPath("$.result.items[2].match.matchId").value(unspecified))
				.andExpect(jsonPath("$.result.items[3].match.matchId").value(belowViewer));
	}

	@Test
	void excludesOppositeGenderFromSameGenderMatchEvenWithAnOppositeGenderQuota() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		long matchId = match(host, original, date, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		jdbc.update("update match set same_gender_only = true where id = ?", matchId);
		String femaleAuth = bearer(viewer());
		String maleAuth = bearer(member(Gender.MALE, "3.0"));

		mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", femaleAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(0));
		mvc.perform(search(femaleAuth, date))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.hasResults").value(false));
		mvc.perform(get("/api/v1/matches").header("Authorization", femaleAuth).param("applicableOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(0));
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", femaleAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.canApply").value(false))
				.andExpect(jsonPath("$.result.applicationBlockReason").value("GENDER_NOT_RECRUITED"));
		mvc.perform(post("/api/v1/matches/{matchId}/applications", matchId).header("Authorization", femaleAuth)
						.contentType(MediaType.APPLICATION_JSON).content("{\"refundPolicyAgreed\":true}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GENDER_NOT_RECRUITED"));
		mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", maleAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[0].match.matchId").value(matchId));
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", maleAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.canApply").value(true));
	}

	@Test
	void ordersRelaxedTimeByRequiredChangeToTheSearchWindow() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		long entirelyOutside = match(host, original, date, "13:00", "2.5", "4.0", MatchType.SINGLES, false);
		long partlyOutside = match(host, original, date, "13:15", "2.5", "4.0", MatchType.SINGLES, false);
		jdbc.update("update court_slot set ends_at = ? where id = (select court_slot_id from match where id = ?)",
				date.atTime(13, 30), entirelyOutside);
		jdbc.update("update court_slot set ends_at = ? where id = (select court_slot_id from match where id = ?)",
				date.atTime(14, 15), partlyOutside);
		mvc.perform(search(bearer(viewer()), date))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("TIME"))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(partlyOutside))
				.andExpect(jsonPath("$.result.similarMatches.items[1].match.matchId").value(entirelyOutside));
	}

	@Test
	void rejectsOffsetAndImpossibleDatesWithCommonRequestError() throws Exception {
		String auth = bearer(viewer());
		for (String invalidDate : Set.of(saturday() + "+09:00", "+02026-11-21", "2026-11-31", "0000-01-01")) {
			mvc.perform(get("/api/v1/matches/search").header("Authorization", auth)
							.param("dates", saturday().toString(), invalidDate).param("areaCodes", MAPO))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON400"));
		}
	}

	@Test
	void requiresBetweenOneAndFourteenDates() throws Exception {
		var auth = bearer(viewer());
		var first = saturday();
		var dates = IntStream.range(0, 15).mapToObj(first::plusDays).toArray(LocalDate[]::new);

		mvc.perform(search(auth)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400"));
		mvc.perform(search(auth, Arrays.copyOf(dates, 14))).andExpect(status().isOk());
		mvc.perform(search(auth, dates)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400"));
	}

	@Test
	void validatesSearchFiltersAndRequiresOnboarding() throws Exception {
		var date = saturday();
		var auth = bearer(viewer());
		mvc.perform(search(auth, date).param("ntrp", "4.5")).andExpect(status().isBadRequest());
		mvc.perform(search(auth, date).param("areaCodes", "99999")).andExpect(status().isBadRequest());
		mvc.perform(search(auth, date).param("areaCodes", JUNG, SEODAEMUN)).andExpect(status().isOk());
		mvc.perform(search(auth, date).param("areaCodes", JUNG, SEODAEMUN, YONGSAN))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
		var waiting = User.signUp();
		entityManager.persist(waiting);
		entityManager.flush();
		mvc.perform(search(bearer(waiting), date))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MATCH_ONBOARDING_REQUIRED"));
		mvc.perform(get("/api/v1/matches/recommendations")).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsInvalidClockTimesIncludingMidnightAsAStartTime() throws Exception {
		String auth = bearer(viewer());
		var date = saturday().toString();
		for (String parameter : Set.of("startTimeFrom", "startTimeTo")) {
			mvc.perform(get("/api/v1/matches/search").header("Authorization", auth)
							.param("dates", date).param("areaCodes", MAPO).param(parameter, "24:01"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON400"));
		}
		mvc.perform(get("/api/v1/matches/search").header("Authorization", auth)
						.param("dates", date).param("areaCodes", MAPO).param("startTimeFrom", "24:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
	}

	private MockHttpServletRequestBuilder search(String auth, LocalDate... dates) {
		return get("/api/v1/matches/search").header("Authorization", auth)
				.param("dates", Arrays.stream(dates).map(LocalDate::toString).toArray(String[]::new))
				.param("areaCodes", MAPO).param("matchTypes", "SINGLES")
				.param("startTimeFrom", "14:00").param("startTimeTo", "17:00");
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
				.minNtrp(min == null ? null : new BigDecimal(min))
				.maxNtrp(max == null ? null : new BigDecimal(max))
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
