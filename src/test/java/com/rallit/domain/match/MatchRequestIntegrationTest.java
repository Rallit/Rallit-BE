package com.rallit.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.court.entity.Court;
import com.rallit.domain.match.dto.request.MatchSearchRequest;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.service.MatchRecommendationService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
	@MockitoSpyBean private MatchRecommendationService recommendations;
	@Autowired private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUpClock() {
		at(LocalDateTime.of(2026, 11, 16, 12, 0));
	}

	@Test
	void persistsConditionsAndReopensResultsWhenOnlySimilarMatchesExist() throws Exception {
		var date = LocalDate.ofInstant(clock.instant(), SEOUL).plusDays(2);
		long similar = recruitMatch(date, "11410", "37.5824", "126.92985", MatchType.SINGLES);
		String auth = bearer(member());

		var result = mvc.perform(create(auth, date.plusDays(1), date))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("COMMON201"))
				.andExpect(jsonPath("$.result.exactMatchCount").value(0))
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andExpect(jsonPath("$.result.request.expiresAt").value(date.plusDays(1) + "T15:00:00Z"))
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
	void returnsTheFullExactCountWhenPreviewContainsOneItem() throws Exception {
		var date = LocalDate.ofInstant(clock.instant(), SEOUL).plusDays(1);
		recruitMatch(date, "11440", "37.560", "126.900", MatchType.SINGLES);
		recruitMatch(date, "11440", "37.562", "126.904", MatchType.SINGLES);

		mvc.perform(create(bearer(member()), date))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result.exactMatchCount").value(2))
				.andExpect(jsonPath("$.result.hasResults").value(true));
	}

	@Test
	void returnsNoResultsWhenBothSearchSectionsAreEmpty() throws Exception {
		var date = LocalDate.ofInstant(clock.instant(), SEOUL).plusDays(1);

		mvc.perform(create(bearer(member()), date))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result.exactMatchCount").value(0))
				.andExpect(jsonPath("$.result.hasResults").value(false));
	}

	@Test
	void readsExistingJsonbConditionsAndUsesThemToSearchAgain() throws Exception {
		var date = LocalDate.ofInstant(clock.instant(), SEOUL).plusDays(1);
		long matchId = recruitMatch(date, "11440", "37.560", "126.900", MatchType.WOMENS_DOUBLES);
		var owner = member();
		var now = clock.instant();
		var savedJson = """
				{"dates":["%s"],"areaCodes":["11440","11410"],"startTimeFrom":"14:00","startTimeTo":"17:00",
				 "matchTypes":["DOUBLES"],"minNtrp":1.0,"maxNtrp":4.0,"sameGenderOnly":false}
				""".formatted(date);
		long id = jdbc.queryForObject("""
				insert into match_request (owner_id, conditions, expires_at, created_at, updated_at)
				values (?, cast(? as jsonb), ?, ?, ?) returning id
				""", Long.class, owner.getId(), savedJson,
				Timestamp.from(date.plusDays(1).atStartOfDay(SEOUL).toInstant()), Timestamp.from(now), Timestamp.from(now));
		entityManager.clear();
		String auth = bearer(owner);

		mvc.perform(get(PATH + "/{id}", id).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.conditions.matchTypes[0]").value("DOUBLES"))
				.andExpect(jsonPath("$.result.conditions.areaCodes[0]").value("11440"))
				.andExpect(jsonPath("$.result.conditions.areaCodes[1]").value("11410"));
		mvc.perform(get(PATH + "/{id}/results", id).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(matchId));
	}

	@Test
	void returnsRequestSpecificOnboardingMessageWithExistingCode() throws Exception {
		var user = User.signUp();
		entityManager.persist(user);
		entityManager.flush();
		String auth = bearer(user);
		var date = LocalDate.ofInstant(clock.instant(), SEOUL).plusDays(1);

		for (var request : List.of(create(auth, date), delete(PATH + "/42").header("Authorization", auth))) {
			mvc.perform(request)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("MATCH_ONBOARDING_REQUIRED"))
					.andExpect(jsonPath("$.message").value("온보딩을 완료해야 매치 요청 기능을 이용할 수 있습니다."));
		}
	}

	@Test
	void rejectsSavingMixedDoublesTogetherWithSameGenderOnly() throws Exception {
		var date = LocalDate.ofInstant(clock.instant(), SEOUL).plusDays(1);
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
		var date = LocalDate.ofInstant(clock.instant(), SEOUL).plusDays(2);
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
	void rejectsPastStartTimeWhenOnlyTodayIsSelected() throws Exception {
		var today = LocalDate.ofInstant(clock.instant(), SEOUL);
		at(today.atTime(15, 0));
		String auth = bearer(member());

		mvc.perform(create(auth, today))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_REQUEST_PAST_START_TIME"));
		mvc.perform(create(auth, today.plusDays(1))).andExpect(status().isCreated());
	}

	@Test
	void preservesMixedDatesAndSearchesOnlyMatchesStillOpenForApplications() throws Exception {
		var today = LocalDate.ofInstant(clock.instant(), SEOUL);
		var tomorrow = today.plusDays(1);
		at(today.atTime(15, 30));
		recruitMatch(today.atTime(14, 0), "11440", "37.560", "126.900", MatchType.SINGLES);
		recruitMatch(today.atTime(16, 0), "11440", "37.560", "126.900", MatchType.SINGLES);
		long todayExact = recruitMatch(today.atTime(17, 0), "11440", "37.560", "126.900", MatchType.SINGLES);
		long todaySimilar = recruitMatch(today.atTime(18, 0), "11440", "37.560", "126.900", MatchType.SINGLES);
		long tomorrowExact = recruitMatch(tomorrow.atTime(13, 0), "11440", "37.560", "126.900", MatchType.SINGLES);
		long tomorrowSimilar = recruitMatch(tomorrow.atTime(12, 0), "11440", "37.560", "126.900", MatchType.SINGLES);
		String auth = bearer(member());

		var response = mvc.perform(create(auth, "13:00", "19:00", today, tomorrow))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result.exactMatchCount").value(2))
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andReturn().getResponse();
		long id = requestId(response.getContentAsString());
		entityManager.flush();
		entityManager.clear();

		mvc.perform(get(PATH + "/{id}", id).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.conditions.dates.length()").value(2))
				.andExpect(jsonPath("$.result.conditions.dates[0]").value(today.toString()))
				.andExpect(jsonPath("$.result.conditions.dates[1]").value(tomorrow.toString()))
				.andExpect(jsonPath("$.result.conditions.startTimeFrom").value("13:00"))
				.andExpect(jsonPath("$.result.conditions.startTimeTo").value("19:00"));
		mvc.perform(get(PATH + "/{id}/results", id).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(2))
				.andExpect(jsonPath("$.result.exactMatches.items[0].match.matchId").value(todayExact))
				.andExpect(jsonPath("$.result.exactMatches.items[1].match.matchId").value(tomorrowExact))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(2))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(todaySimilar))
				.andExpect(jsonPath("$.result.similarMatches.items[1].match.matchId").value(tomorrowSimilar))
				.andExpect(jsonPath("$.result.relaxedConditions[0]").value("TIME"));
	}

	@Test
	void continuesSearchingFutureDatesAfterTodaysTimeWindowEnds() throws Exception {
		var today = LocalDate.ofInstant(clock.instant(), SEOUL);
		var tomorrow = today.plusDays(1);
		at(today.atTime(19, 0));
		recruitMatch(today.atTime(17, 0), "11440", "37.560", "126.900", MatchType.SINGLES);
		long tomorrowSimilar = recruitMatch(tomorrow.atTime(12, 0), "11440", "37.560", "126.900", MatchType.SINGLES);
		var owner = member();
		String auth = bearer(owner);

		var response = mvc.perform(create(auth, "13:00", "19:00", today, tomorrow))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result.exactMatchCount").value(0))
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andReturn().getResponse();
		long id = requestId(response.getContentAsString());
		mvc.perform(get(PATH + "/{id}/results", id).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.hasResults").value(true))
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(0))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(1))
				.andExpect(jsonPath("$.result.similarMatches.items[0].match.matchId").value(tomorrowSimilar));

		at(tomorrow.atTime(19, 0));
		mvc.perform(get(PATH + "/{id}/results", id).header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.hasResults").value(false))
				.andExpect(jsonPath("$.result.exactMatches.totalElements").value(0))
				.andExpect(jsonPath("$.result.similarMatches.totalElements").value(0));
	}

	@Test
	void rejectsTodayWhenPreviewFinishesAtTheSelectedStartTime() throws Exception {
		var today = LocalDate.ofInstant(clock.instant(), SEOUL);
		var owner = member();
		String auth = bearer(owner);
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			at(today.atTime(14, 0));
			return result;
		}).when(recommendations).search(eq(owner.getId()), any(MatchSearchRequest.class), eq(0), eq(0), eq(1));

		mvc.perform(create(auth, today))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_REQUEST_PAST_START_TIME"));
		mvc.perform(get(PATH).header("Authorization", bearer(owner)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(0));
	}

	@Test
	void limitsActiveRequestsAndFreesSpaceExactlyAtSeoulMidnight() throws Exception {
		var today = LocalDate.ofInstant(clock.instant(), SEOUL);
		var midnight = today.plusDays(1).atStartOfDay();
		var owner = member();
		String initialAuth = bearer(owner);
		long expiring = savedRequest(initialAuth, today);
		savedRequest(initialAuth, today.plusDays(1));
		savedRequest(initialAuth, today.plusDays(2));

		at(midnight.minusSeconds(1));
		String auth = bearer(owner);
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
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void searchesBeforeUserLockAndRejectsStartTimePassedWhileWaiting() throws Exception {
		var tx = new TransactionTemplate(transactionManager);
		var owner = tx.execute(status -> member());
		var today = LocalDate.ofInstant(clock.instant(), SEOUL);
		var previewFinished = new CountDownLatch(1);
		try (var pool = Executors.newSingleThreadExecutor()) {
			String auth = bearer(owner);
			doAnswer(invocation -> {
				Object result = invocation.callRealMethod();
				previewFinished.countDown();
				return result;
			}).when(recommendations).search(eq(owner.getId()), any(MatchSearchRequest.class), eq(0), eq(0), eq(1));

			var response = tx.execute(status -> {
				jdbc.queryForObject("select id from users where id = ? for update", Long.class, owner.getId());
				var pending = pool.submit(() -> mvc.perform(create(auth, today)).andReturn().getResponse());
				await().alias("preview completes while the user row is locked")
						.pollInSameThread().atMost(10, TimeUnit.SECONDS)
						.until(() -> previewFinished.getCount() == 0);
				// Poll on the lock holder's connection: the other pool connection is waiting for this lock.
				await().alias("creation waits for the user lock")
						.pollInSameThread().atMost(10, TimeUnit.SECONDS)
						.until(() -> Boolean.TRUE.equals(jdbc.queryForObject("""
								select exists (
								    select 1 from pg_stat_activity
								    where pg_backend_pid() = any(pg_blocking_pids(pid))
								)
								""", Boolean.class)));
				at(today.atTime(14, 0));
				return pending;
			});

			var result = response.get(10, TimeUnit.SECONDS);
			assertThat(result.getStatus()).isEqualTo(400);
			assertThat((String) JsonPath.read(result.getContentAsString(), "$.code"))
					.isEqualTo("MATCH_REQUEST_PAST_START_TIME");
			assertThat(jdbc.queryForObject("select count(*) from match_request where owner_id = ?",
					Long.class, owner.getId())).isZero();
		} finally {
			tx.executeWithoutResult(status -> {
				jdbc.update("delete from match_request where owner_id = ?", owner.getId());
				entityManager.remove(entityManager.find(User.class, owner.getId()));
			});
		}
	}

	@Test
	void rejectsSavingDatesOutsideTheSupportedWindow() throws Exception {
		var today = LocalDate.ofInstant(clock.instant(), SEOUL);
		String auth = bearer(member());
		for (var invalid : Set.of(today.minusDays(1), today.plusDays(14))) {
			mvc.perform(create(auth, invalid))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_REQUEST_INVALID_DATE"));
		}
		mvc.perform(create(auth, today.plusDays(13))).andExpect(status().isCreated());
	}

	@Test
	void requiresBetweenOneAndFourteenDates() throws Exception {
		var auth = bearer(member());
		var today = LocalDate.ofInstant(clock.instant(), SEOUL);
		var dates = IntStream.range(0, 15).mapToObj(today::plusDays).toArray(LocalDate[]::new);

		mvc.perform(create(auth)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400"));
		mvc.perform(create(auth, Arrays.copyOf(dates, 14))).andExpect(status().isCreated());
		mvc.perform(create(auth, dates)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400"));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void allowsOnlyOneConcurrentCreateWhenTwoRequestsAreAlreadyActive() throws Exception {
		var tx = new TransactionTemplate(transactionManager);
		var user = tx.execute(status -> member());
		var date = LocalDate.ofInstant(clock.instant(), SEOUL).plusDays(2);
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
		return create(auth, "14:00", "17:00", dates);
	}

	private MockHttpServletRequestBuilder create(String auth, String from, String to, LocalDate... dates) {
		String dateJson = Arrays.stream(dates).map(date -> "\"" + date + "\"")
				.collect(Collectors.joining(",", "[", "]"));
		return post(PATH).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("""
				{"dates":%s,"areaCodes":["11440"],"startTimeFrom":"%s","startTimeTo":"%s",
				 "matchTypes":["SINGLES"],"minNtrp":1.0,"maxNtrp":4.0,"sameGenderOnly":false}
				""".formatted(dateJson, from, to));
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

	private long recruitMatch(LocalDate date, String areaCode, String latitude, String longitude, MatchType type) {
		return recruitMatch(date.atTime(14, 0), areaCode, latitude, longitude, type);
	}

	private long recruitMatch(LocalDateTime seoulStartsAt, String areaCode, String latitude, String longitude,
			MatchType type) {
		var startsAt = seoulStartsAt.atZone(SEOUL).toInstant();
		var host = member();
		var area = entityManager.createQuery("select a from Area a where a.code = :areaCode", Area.class)
				.setParameter("areaCode", areaCode)
				.getSingleResult();
		var court = Court.builder().area(area).name("테스트 테니스장").address("서울특별시")
				.latitude(new BigDecimal(latitude)).longitude(new BigDecimal(longitude)).build();
		entityManager.persist(court);
		var slot = EntityFixtures.courtSlot(court, host, "1", startsAt);
		entityManager.persist(slot);
		var match = Match.builder().courtSlot(slot).host(host).matchTypes(Set.of(type))
				.maleRecruitCount(1).femaleRecruitCount(1).participationFee(10000)
				.recruitmentDeadline(startsAt.minus(Duration.ofHours(1))).build();
		entityManager.persist(match);
		entityManager.flush();
		jdbc.update("update court_slot set status = 'APPROVED' where id = ?", slot.getId());
		jdbc.update("update match set status = 'RECRUITING' where id = ?", match.getId());
		entityManager.clear();
		return match.getId();
	}
}
