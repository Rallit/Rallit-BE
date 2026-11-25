package com.rallit.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.court.entity.Court;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.region.repository.AreaRepository;
import com.rallit.domain.user.entity.OnboardingProfile;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.enums.PreferredTimeSlot;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
@Transactional
class MatchRecommendationIntegrationTest extends IntegrationTestSupport {

	private static final String MATCH_REQUESTS = "/api/v1/match-requests";
	private static final String MAPO = "11440";
	private static final String JUNG = "11140";
	private static final String SEODAEMUN = "11410";
	private static final String YONGSAN = "11170";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager entityManager;
	@Autowired private AreaRepository areas;
	@Autowired private TokenSessionService sessions;
	@Autowired private Clock clock;

	@Test
	void emptySearchIsAValidResponseForAnOnboardedMember() throws Exception {
		search(bearer(viewer()), saturday())
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

		search(auth, date)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(exact))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(similar))
				.andExpect(jsonPath("$.result.similarMatches.items[0].areaName").value("서대문구"))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("AREA"));
		for (String pageParam : Set.of("exactPage", "similarPage")) {
			search(auth, searchConditions(date), Map.of(pageParam, "1", "size", "1"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("MATCH_PAGE_OUT_OF_RANGE"));
		}
		mvc.perform(get("/api/v1/matches/{matchId}", similar).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.matchId").value(similar));
	}

	@Test
	void exposesTheSameCourtThumbnailInMatchCardsDetailAndSavedRequestResults() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		var nearby = court(SEODAEMUN, "37.5824", "126.92985");
		long exact = match(host, original, date, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		long similar = match(host, nearby, date, "15:00", "2.5", "4.0", MatchType.SINGLES, false);
		jdbc.update("update court set thumbnail_image_key = ? where id = ?", UUID.randomUUID() + ".png",
				original.getId());
		entityManager.clear();
		String url = "/api/v1/courts/" + original.getId() + "/thumbnail";
		String auth = bearer(viewer());

		mvc.perform(get("/api/v1/matches").param("date", date.toString()).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[0].courtThumbnailUrl").value(url))
				.andExpect(jsonPath("$.result.items[1].courtThumbnailUrl").value(nullValue()));
		mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[0].match.courtThumbnailUrl").value(url));
		search(auth, date)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.courtThumbnailUrl").value(url))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.courtThumbnailUrl").value(nullValue()));
		mvc.perform(get("/api/v1/matches/{matchId}", exact).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.court.thumbnailUrl").value(url))
				.andExpect(jsonPath("$.result.court.thumbnailImageKey").doesNotExist());
		mvc.perform(get("/api/v1/matches/{matchId}", similar).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.court.thumbnailUrl").value(nullValue()));
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

		search(auth, searchConditions(lastDate, firstDate, firstDate), Map.of("size", "1", "exactPage", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(2))
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(last))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(similar))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("AREA"));
		search(auth, lastDate, firstDate)
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
		match(host, nearby, date, "14:00", "2.0", "2.5", MatchType.SINGLES, true);
		match(host, nearby, date, "14:00", "2.5", "4.0", MatchType.RALLY, true);
		match(host, tooFar, date, "14:00", "2.5", "4.0", MatchType.SINGLES, true);
		match(viewer, nearby, date, "14:00", "2.5", "4.0", MatchType.SINGLES, true);
		String auth = bearer(viewer);
		var conditions = searchConditions(date);
		conditions.set("minNtrp", "3.0");
		conditions.set("sameGenderOnly", "true");

		search(auth, conditions)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(0))
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(timeOnly))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("TIME"));
		jdbc.update("update match set status = 'CANCELED' where id = ?", timeOnly);
		entityManager.clear();
		search(auth, conditions)
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
		var conditions = searchConditions(date);
		conditions.add("areaCodes", JUNG);

		search(auth, conditions)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(similar))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("AREA"));
		conditions.set("areaCodes", MAPO);
		conditions.set("matchTypes", "RALLY");
		conditions.set("startTimeFrom", "23:00");
		conditions.set("startTimeTo", "24:00");
		search(auth, conditions)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(endsAtMidnight))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("TIME"));
		conditions.set("startTimeFrom", "22:00");
		search(auth, conditions)
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

	@ParameterizedTest
	@CsvSource({
			"1, WEEKEND_MORNING, 11:59, 12:00",
			"2, WEEKDAY_AFTERNOON, 12:00, 11:59"
	})
	void classifiesSundayMorningAndWeekdayNoon(int daysAfterSaturday, PreferredTimeSlot preference,
			String matchingTime, String excludedTime) throws Exception {
		var date = saturday().plusDays(daysAfterSaturday);
		var host = member(Gender.MALE, "3.0");
		var viewer = member(Gender.FEMALE, "4.0", preference);
		var original = court(MAPO, "37.560", "126.900");
		long expected = match(host, original, date, matchingTime, "3.5", "4.0", MatchType.SINGLES, false);
		match(host, original, date, excludedTime, "3.5", "4.0", MatchType.SINGLES, false);

		mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(1))
				.andExpect(jsonPath("$.result.items[0].match.matchId").value(expected));
	}

	@Test
	void includesOverlappingAndUnrestrictedNtrpRangesInExactAndSimilarResults() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		var nearby = court(SEODAEMUN, "37.5824", "126.92985");
		long lowerBoundOnly = match(host, original, date, "14:00", "2.0", null, MatchType.SINGLES, false);
		long upperBoundOnly = match(host, nearby, date, "14:00", null, "1.0", MatchType.SINGLES, false);
		long unspecifiedExact = match(host, original, date, "14:30", null, null, MatchType.SINGLES, false);
		long unspecifiedSimilar = match(host, nearby, date, "14:30", null, null, MatchType.SINGLES, false);
		long partialOverlap = match(host, original, date, "14:45", "1.5", "2.5", MatchType.SINGLES, false);
		match(host, original, date, "15:00", "2.5", null, MatchType.SINGLES, false);
		match(host, nearby, date, "15:00", "2.5", "3.0", MatchType.SINGLES, false);
		String auth = bearer(viewer());
		var conditions = searchConditions(date);
		conditions.set("maxNtrp", "2.0");

		search(auth, conditions)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(3))
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(lowerBoundOnly))
				.andExpect(jsonPath("$.result.exactMatches.items[1].match.matchId").value(unspecifiedExact))
				.andExpect(jsonPath("$.result.exactMatches.items[2].match.matchId").value(partialOverlap))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(2))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(upperBoundOnly))
				.andExpect(jsonPath("$.result.similarMatches.items[1].match.matchId").value(unspecifiedSimilar));
		conditions.set("minNtrp", "1.5");
		search(auth, conditions)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(3))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(unspecifiedSimilar));
		search(auth, date)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(4))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(3));
	}

	@ParameterizedTest
	@CsvSource({"3.5, 3.0", "3.1, 4.0", "3.0, 4.5"})
	void rejectsReversedOrUnsupportedNtrpRanges(String minNtrp, String maxNtrp) throws Exception {
		var auth = bearer(viewer());
		var conditions = searchConditions(saturday());
		conditions.set("minNtrp", minNtrp);
		conditions.set("maxNtrp", maxNtrp);

		mvc.perform(createRequest(auth, conditions))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_INVALID_SEARCH_NTRP"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"dates", "areaCodes", "startTimeFrom", "startTimeTo", "matchTypes",
			"minNtrp", "maxNtrp", "sameGenderOnly"})
	void rejectsOmittingAnySearchCondition(String field) throws Exception {
		var conditions = searchConditions(saturday());
		conditions.remove(field);

		mvc.perform(createRequest(bearer(viewer()), conditions))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
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
		search(femaleAuth, date)
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
	void blocksReapplicationAfterHostRejectionButKeepsTheMatchInTheGeneralList() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		long matchId = match(host, original, date, "14:00", "2.5", "4.0", MatchType.SINGLES, false);
		String auth = bearer(viewer());
		String created = mvc.perform(post("/api/v1/matches/{matchId}/applications", matchId)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON).content("{\"refundPolicyAgreed\":true}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long applicationId = ((Number) JsonPath.read(created, "$.result.applicationId")).longValue();
		mvc.perform(post("/api/v1/host/matches/{matchId}/applications/{id}/reject", matchId, applicationId)
						.header("Authorization", bearer(host)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("REJECTED"));

		mvc.perform(post("/api/v1/matches/{matchId}/applications", matchId).header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON).content("{\"refundPolicyAgreed\":true}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_REJECTED"));
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.canApply").value(false))
				.andExpect(jsonPath("$.result.applicationBlockReason").value("APPLICATION_REJECTED"));
		mvc.perform(get("/api/v1/matches").header("Authorization", auth).param("date", date.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(1))
				.andExpect(jsonPath("$.result.items[0].matchId").value(matchId));
		mvc.perform(get("/api/v1/matches").header("Authorization", auth).param("date", date.toString())
						.param("applicableOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(0));
		mvc.perform(get("/api/v1/matches/recommendations").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalElements").value(0));
		search(auth, date)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.hasResults").value(false));
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", bearer(viewer())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.canApply").value(true));

		for (int i = 0; i < 2; i++) {
			var otherApplicant = viewer();
			jdbc.update("""
					insert into match_application (match_id, applicant_user_id, applicant_gender, status,
						refund_policy_agreed_at, created_at, updated_at)
					values (?, ?, 'FEMALE', 'CONFIRMED', now(), now(), now())
					""", matchId, otherApplicant.getId());
		}
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.applicationBlockReason").value("APPLICATION_REJECTED"));
	}

	@Test
	void ordersRelaxedTimeByRequiredChangeToTheSearchWindow() throws Exception {
		var date = saturday();
		var host = member(Gender.MALE, "3.0");
		var original = court(MAPO, "37.560", "126.900");
		long entirelyOutside = match(host, original, date, "13:00", "2.5", "4.0", MatchType.SINGLES, false);
		long partlyOutside = match(host, original, date, "13:15", "2.5", "4.0", MatchType.SINGLES, false);
		jdbc.update("update court_slot set ends_at = ? where id = (select court_slot_id from match where id = ?)",
				Timestamp.from(date.atTime(13, 30).atZone(SEOUL).toInstant()), entirelyOutside);
		jdbc.update("update court_slot set ends_at = ? where id = (select court_slot_id from match where id = ?)",
				Timestamp.from(date.atTime(14, 15).atZone(SEOUL).toInstant()), partlyOutside);
		search(bearer(viewer()), date)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("TIME"))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(partlyOutside))
				.andExpect(jsonPath("$.result.similarMatches.items[1].match.matchId").value(entirelyOutside));
	}

	@Test
	void rejectsOffsetAndImpossibleDatesWithCommonRequestError() throws Exception {
		String auth = bearer(viewer());
		for (String invalidDate : Set.of(saturday() + "+09:00", "+02026-11-21", "2026-11-31", "0000-01-01")) {
			var conditions = searchConditions(saturday());
			conditions.add("dates", invalidDate);
			mvc.perform(createRequest(auth, conditions))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON400"));
		}
	}

	@Test
	void validatesSearchFiltersAndRequiresOnboarding() throws Exception {
		var date = saturday();
		var auth = bearer(viewer());
		var conditions = searchConditions(date);
		conditions.set("areaCodes", "99999");
		mvc.perform(createRequest(auth, conditions))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_INVALID_SEARCH_AREA"));
		conditions.put("areaCodes", List.of(MAPO, JUNG, SEODAEMUN));
		mvc.perform(createRequest(auth, conditions)).andExpect(status().isCreated());
		conditions.put("areaCodes", List.of(MAPO, JUNG, SEODAEMUN, YONGSAN));
		mvc.perform(createRequest(auth, conditions))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
		var waiting = User.signUp();
		entityManager.persist(waiting);
		entityManager.flush();
		mvc.perform(createRequest(bearer(waiting), searchConditions(date)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MATCH_ONBOARDING_REQUIRED"));
		mvc.perform(get("/api/v1/matches/recommendations")).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsInvalidClockTimesIncludingMidnightAsAStartTime() throws Exception {
		String auth = bearer(viewer());
		for (String parameter : Set.of("startTimeFrom", "startTimeTo")) {
			var conditions = searchConditions(saturday());
			conditions.set(parameter, "24:01");
			mvc.perform(createRequest(auth, conditions))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON400"));
		}
		var conditions = searchConditions(saturday());
		conditions.set("startTimeFrom", "24:00");
		mvc.perform(createRequest(auth, conditions))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
	}

	private ResultActions search(String auth, LocalDate... dates) throws Exception {
		return search(auth, searchConditions(dates));
	}

	private ResultActions search(String auth, MultiValueMap<String, String> conditions) throws Exception {
		return search(auth, conditions, Map.of());
	}

	private ResultActions search(String auth, MultiValueMap<String, String> conditions,
			Map<String, String> pageParameters) throws Exception {
		String created = mvc.perform(createRequest(auth, conditions)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.result.request.requestId")).longValue();
		try {
			var results = get(MATCH_REQUESTS + "/{id}/results", id).header("Authorization", auth);
			pageParameters.forEach((name, value) -> results.param(name, value));
			return mvc.perform(results);
		} finally {
			mvc.perform(delete(MATCH_REQUESTS + "/{id}", id).header("Authorization", auth))
					.andExpect(status().isNoContent());
		}
	}

	private MockHttpServletRequestBuilder createRequest(String auth, MultiValueMap<String, String> conditions) {
		var body = new LinkedHashMap<String, Object>();
		conditions.forEach((field, values) -> body.put(field, switch (field) {
			case "dates", "areaCodes", "matchTypes" -> values;
			case "minNtrp", "maxNtrp" -> new BigDecimal(values.getFirst());
			case "sameGenderOnly" -> Boolean.valueOf(values.getFirst());
			default -> values.getFirst();
		}));
		return post(MATCH_REQUESTS).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body));
	}

	private MultiValueMap<String, String> searchConditions(LocalDate... dates) {
		var conditions = new LinkedMultiValueMap<String, String>();
		Arrays.stream(dates).map(LocalDate::toString).forEach(date -> conditions.add("dates", date));
		conditions.set("areaCodes", MAPO);
		conditions.set("matchTypes", "SINGLES");
		conditions.set("startTimeFrom", "14:00");
		conditions.set("startTimeTo", "17:00");
		conditions.set("minNtrp", "1.0");
		conditions.set("maxNtrp", "4.0");
		conditions.set("sameGenderOnly", "false");
		return conditions;
	}

	private User viewer() {
		return member(Gender.FEMALE, "4.0");
	}

	private User member(Gender gender, String ntrp) {
		return member(gender, ntrp, PreferredTimeSlot.WEEKEND_AFTERNOON);
	}

	private User member(Gender gender, String ntrp, PreferredTimeSlot preference) {
		var area = areas.findAllByCodeIn(Set.of(MAPO)).getFirst();
		var user = User.signUp();
		user.completeOnboarding(new OnboardingProfile("추천" + UUID.randomUUID().toString().substring(0, 8),
				gender, AgeGroup.TWENTIES, EntityFixtures.TENNIS_STARTED_ON, new BigDecimal(ntrp), false,
				Set.of(PreferredMatchType.SINGLES, PreferredMatchType.DOUBLES), Set.of(area),
				Set.of(preference)));
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
		var startsAt = date.atTime(LocalTime.parse(start)).atZone(SEOUL).toInstant();
		var slot = EntityFixtures.courtSlot(court, host, UUID.randomUUID().toString().substring(0, 8), startsAt);
		entityManager.persist(slot);
		var match = Match.builder().courtSlot(slot).host(host).matchTypes(Set.of(type))
				.maleRecruitCount(sameGenderOnly ? 0 : 2).femaleRecruitCount(2)
				.sameGenderOnly(sameGenderOnly)
				.minNtrp(min == null ? null : new BigDecimal(min))
				.maxNtrp(max == null ? null : new BigDecimal(max))
				.participationFee(10000).recruitmentDeadline(startsAt.minus(1, ChronoUnit.HOURS)).build();
		entityManager.persist(match);
		entityManager.flush();
		jdbc.update("update court_slot set status = 'APPROVED' where id = ?", slot.getId());
		jdbc.update("update match set status = 'RECRUITING' where id = ?", match.getId());
		return match.getId();
	}

	private LocalDate saturday() {
		return LocalDate.ofInstant(clock.instant(), SEOUL).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
	}

	private String bearer(User user) {
		entityManager.clear();
		return "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
	}
}
