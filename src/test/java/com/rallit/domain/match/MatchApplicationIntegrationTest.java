package com.rallit.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.domain.court.repository.CourtSlotRepository;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.exception.MatchApplicationErrorStatus;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.match.service.MatchApplicationService;
import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.entity.OnboardingProfile;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.ExperiencePeriod;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.enums.PreferredTimeSlot;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class MatchApplicationIntegrationTest extends IntegrationTestSupport {

	private static final String AGREED = "{\"refundPolicyAgreed\":true}";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private TokenSessionService sessions;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourtRepository courtRepository;

	@Autowired
	private CourtSlotRepository courtSlotRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private MatchApplicationService matchApplicationService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private HikariDataSource dataSource;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private Clock clock;

	private final ExecutorService executor = Executors.newFixedThreadPool(5);

	private final List<Long> userIds = new ArrayList<>();

	private final List<Long> courtIds = new ArrayList<>();

	private final List<Long> matchIds = new ArrayList<>();

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
		matchIds.forEach(id -> jdbcTemplate.update("DELETE FROM match_application WHERE match_id = ?", id));
		matchIds.forEach(id -> jdbcTemplate.update("DELETE FROM match WHERE id = ?", id));
		courtIds.forEach(id -> jdbcTemplate.update("DELETE FROM court_slot WHERE court_id = ?", id));
		courtIds.forEach(id -> jdbcTemplate.update("DELETE FROM court WHERE id = ?", id));
		userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
	}

	@Test
	void applyCreatesPendingApplicationWithServerTimes() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);
		LocalDateTime before = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);

		long applicationId = id(apply(applicant, match.getId(), AGREED)
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/applications/")))
				.andExpect(jsonPath("$.code").value("COMMON201"))
				.andExpect(jsonPath("$.result.matchId").value(match.getId()))
				.andExpect(jsonPath("$.result.status").value("PENDING"))
				.andExpect(jsonPath("$.result.appliedAt").isString()));

		var row = jdbcTemplate.queryForMap("SELECT * FROM match_application WHERE id = ?", applicationId);
		assertThat(row.get("status")).isEqualTo("PENDING");
		assertThat(row.get("applicant_user_id")).isEqualTo(applicant.getId());
		assertThat(row.get("applicant_gender")).isEqualTo("FEMALE");
		assertThat(((java.sql.Timestamp) row.get("refund_policy_agreed_at")).toLocalDateTime())
				.isAfterOrEqualTo(before);
		assertThat(row.get("canceled_at")).isNull();
	}

	@Test
	void applyRequiresRefundPolicyAgreement() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);

		apply(applicant, match.getId(), "{\"refundPolicyAgreed\":false}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.result.refundPolicyAgreed").exists());
		apply(applicant, match.getId(), "{}")
				.andExpect(status().isBadRequest());

		assertThat(applicationCount(match.getId())).isZero();
	}

	@Test
	void rejectsDuplicateActiveApplication() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);
		apply(applicant, match.getId(), AGREED).andExpect(status().isCreated());

		apply(applicant, match.getId(), AGREED)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_DUPLICATED"));

		assertThat(applicationCount(match.getId())).isEqualTo(1);
	}

	@Test
	void concurrentApplicationsFromSameUserCreateSingleRow() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Throwable>> results = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			results.add(executor.submit(() -> {
				start.await();
				return catchThrowable(() -> matchApplicationService.apply(applicant.getId(), match.getId()));
			}));
		}
		start.countDown();

		List<Throwable> failures = new ArrayList<>();
		for (Future<Throwable> result : results) {
			Throwable failure = result.get(30, TimeUnit.SECONDS);
			if (failure != null) {
				failures.add(failure);
			}
		}

		assertThat(failures).hasSize(4).allSatisfy(failure -> assertThat(failure)
				.isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
						.isEqualTo(MatchApplicationErrorStatus.APPLICATION_DUPLICATED)));
		assertThat(applicationCount(match.getId())).isEqualTo(1);
	}

	@Test
	void translatesUniqueConstraintViolationWhenPreCheckIsBypassedByRace() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);
		CountDownLatch inserted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
				status -> {
					jdbcTemplate.update("""
							INSERT INTO match_application (match_id, applicant_user_id, applicant_gender, status,
							    refund_policy_agreed_at, created_at, updated_at)
							VALUES (?, ?, 'FEMALE', 'PENDING', now(), now(), now())
							""", match.getId(), applicant.getId());
					inserted.countDown();
					try {
						release.await(30, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}));
		assertThat(inserted.await(30, TimeUnit.SECONDS)).isTrue();

		Future<Throwable> applying = executor.submit(
				() -> catchThrowable(() -> matchApplicationService.apply(applicant.getId(), match.getId())));
		awaitBlockedOnLock();
		release.countDown();
		holder.get(30, TimeUnit.SECONDS);

		assertThat(applying.get(30, TimeUnit.SECONDS))
				.isInstanceOfSatisfying(GeneralException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo(MatchApplicationErrorStatus.APPLICATION_DUPLICATED);
					assertThat(exception.getCause()).isInstanceOf(DataIntegrityViolationException.class);
				});
		assertThat(applicationCount(match.getId())).isEqualTo(1);
	}

	@Test
	void rejectsMatchThatIsNotRecruitingOrPastDeadline() throws Exception {
		User host = activeUser(Gender.MALE);
		User applicant = activeUser(Gender.FEMALE);
		Match pendingApproval = match(host, Set.of(MatchType.MIXED_DOUBLES), 1, 2, LocalDateTime.now(clock).plusDays(1));
		Match closed = match(host, Set.of(MatchType.MIXED_DOUBLES), 1, 2, LocalDateTime.now(clock).minusMinutes(1));
		recruit(closed);
		Match started = recruitingMatch(host, LocalDateTime.now(clock).minusMinutes(1));

		apply(applicant, pendingApproval.getId(), AGREED)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MATCH_NOT_RECRUITING"));
		apply(applicant, closed.getId(), AGREED)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MATCH_RECRUITMENT_CLOSED"));
		apply(applicant, started.getId(), AGREED)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MATCH_RECRUITMENT_CLOSED"));
		apply(applicant, Long.MAX_VALUE, AGREED)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));
	}

	@Test
	void rejectsHostApplyingToOwnMatch() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host);

		apply(host, match.getId(), AGREED)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("HOST_NOT_ALLOWED"));
		assertThat(applicationCount(match.getId())).isZero();
	}

	@Test
	void rejectsGenderWhoseSeatsAreAllConfirmedButNotOnlyPending() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		long firstMaleId = id(apply(activeUser(Gender.MALE), match.getId(), AGREED));
		apply(activeUser(Gender.MALE), match.getId(), AGREED).andExpect(status().isCreated());
		jdbcTemplate.update("UPDATE match_application SET status = 'CONFIRMED' WHERE id = ?", firstMaleId);

		apply(activeUser(Gender.MALE), match.getId(), AGREED)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RECRUITMENT_FULL"));
		apply(activeUser(Gender.FEMALE), match.getId(), AGREED).andExpect(status().isCreated());
	}

	@Test
	void validatesGenderRecruitmentButNotNtrpOrAgePreference() throws Exception {
		Match womensDoubles = match(activeUser(Gender.FEMALE), Set.of(MatchType.WOMENS_DOUBLES), 0, 3,
				LocalDateTime.now(clock).plusDays(1));
		recruit(womensDoubles);
		User female = saveUser(onboarded(Gender.FEMALE, new BigDecimal("4.0"), AgeGroup.FIFTIES));

		apply(activeUser(Gender.MALE), womensDoubles.getId(), AGREED)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GENDER_NOT_RECRUITED"));
		apply(female, womensDoubles.getId(), AGREED)
				.andExpect(status().isCreated());
	}

	@Test
	void rejectsUnauthenticatedAndOnboardingUsers() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User onboarding = saveUser(User.signUp());

		mvc.perform(post("/api/v1/matches/{matchId}/applications", match.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(AGREED))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/me/applications"))
				.andExpect(status().isUnauthorized());
		apply(onboarding, match.getId(), AGREED)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
		assertThat(applicationCount(match.getId())).isZero();
	}

	@Test
	void listsMyActiveApplicationsFromTodayInMatchTimeOrderWithParticipantCounts() throws Exception {
		User host = activeUser(Gender.MALE);
		LocalDate today = LocalDate.now(clock);
		Match yesterday = recruitingMatch(host);
		Match earlyToday = recruitingMatch(host);
		Match tomorrow = match(host, Set.of(MatchType.MENS_DOUBLES, MatchType.RALLY), 1, 2,
				LocalDateTime.now(clock).plusDays(1), today.plusDays(1).atTime(10, 0));
		recruit(tomorrow);
		Match canceledTomorrow = recruitingMatch(host, today.plusDays(1).atTime(14, 0));
		Match nextWeek = recruitingMatch(host, today.plusDays(7).atTime(9, 0));
		User applicant = activeUser(Gender.FEMALE);
		long yesterdayId = id(apply(applicant, yesterday.getId(), AGREED));
		long earlyTodayId = id(apply(applicant, earlyToday.getId(), AGREED));
		long tomorrowId = id(apply(applicant, tomorrow.getId(), AGREED));
		long canceledId = id(apply(applicant, canceledTomorrow.getId(), AGREED));
		long nextWeekId = id(apply(applicant, nextWeek.getId(), AGREED));
		long confirmedOtherId = id(apply(activeUser(Gender.MALE), tomorrow.getId(), AGREED));
		apply(activeUser(Gender.FEMALE), tomorrow.getId(), AGREED).andExpect(status().isCreated());
		apply(activeUser(Gender.FEMALE), earlyToday.getId(), AGREED).andExpect(status().isCreated());
		jdbcTemplate.update("UPDATE match_application SET status = 'PAYMENT_PENDING' WHERE id = ?", tomorrowId);
		jdbcTemplate.update("UPDATE match_application SET status = 'CONFIRMED' WHERE id IN (?, ?)", confirmedOtherId,
				nextWeekId);
		cancel(applicant, canceledId, null).andExpect(status().isOk());
		reschedule(yesterday, today.minusDays(1).atTime(10, 0));
		reschedule(earlyToday, today.atStartOfDay());

		myApplications(applicant)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items.length()").value(3))
				.andExpect(jsonPath("$.result.items[0].applicationId").value(earlyTodayId))
				.andExpect(jsonPath("$.result.items[0].applicationStatus").value("PENDING"))
				.andExpect(jsonPath("$.result.items[0].matchStatus").value("RECRUITING"))
				.andExpect(jsonPath("$.result.items[0].matchTypes[0]").value("MIXED_DOUBLES"))
				.andExpect(jsonPath("$.result.items[0].courtName").value("장충테니스장"))
				.andExpect(jsonPath("$.result.items[0].courtNumber").value("1"))
				.andExpect(jsonPath("$.result.items[0].participationFee").value(3500))
				.andExpect(jsonPath("$.result.items[0].currentParticipantCount").value(1))
				.andExpect(jsonPath("$.result.items[0].capacity").value(4))
				.andExpect(jsonPath("$.result.items[1].applicationId").value(tomorrowId))
				.andExpect(jsonPath("$.result.items[1].applicationStatus").value("PAYMENT_PENDING"))
				.andExpect(jsonPath("$.result.items[1].matchTypes[0]").value("RALLY"))
				.andExpect(jsonPath("$.result.items[1].matchTypes[1]").value("MENS_DOUBLES"))
				.andExpect(jsonPath("$.result.items[1].currentParticipantCount").value(3))
				.andExpect(jsonPath("$.result.items[1].capacity").value(4))
				.andExpect(jsonPath("$.result.items[2].applicationId").value(nextWeekId))
				.andExpect(jsonPath("$.result.items[2].applicationStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.result.hasNext").value(false));
		myApplications(applicant, "size", "2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items.length()").value(2))
				.andExpect(jsonPath("$.result.page").value(0))
				.andExpect(jsonPath("$.result.size").value(2))
				.andExpect(jsonPath("$.result.hasNext").value(true))
				.andExpect(jsonPath("$.result.totalElements").value(3));
		myApplications(applicant, "page", "4")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items.length()").value(0))
				.andExpect(jsonPath("$.result.page").value(4))
				.andExpect(jsonPath("$.result.size").value(20))
				.andExpect(jsonPath("$.result.hasNext").value(false))
				.andExpect(jsonPath("$.result.totalElements").value(3));
		myApplications(applicant, "date", today.plusDays(1).toString(), "size", "1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items.length()").value(1))
				.andExpect(jsonPath("$.result.items[0].applicationId").value(tomorrowId))
				.andExpect(jsonPath("$.result.totalElements").value(1))
				.andExpect(jsonPath("$.result.hasNext").value(false));
		myApplications(applicant, "date", today.minusDays(1).toString())
				.andExpect(jsonPath("$.result.items.length()").value(1))
				.andExpect(jsonPath("$.result.items[0].applicationId").value(yesterdayId));
		myApplications(applicant, "date", today.plusDays(2).toString())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items.length()").value(0))
				.andExpect(jsonPath("$.result.totalElements").value(0));
		myApplications(applicant, "date", "2026-13-01").andExpect(status().isBadRequest());
		myApplications(applicant, "size", "51").andExpect(status().isBadRequest());
		myApplications(applicant, "page", "10001")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
	}

	@Test
	void listsMyClosedApplicationsByRecentChange() throws Exception {
		User host = activeUser(Gender.MALE);
		User applicant = activeUser(Gender.FEMALE);
		long rejectedId = id(apply(applicant, recruitingMatch(host).getId(), AGREED));
		jdbcTemplate.update("UPDATE match_application SET status = 'REJECTED', reject_reason = 'SKILL_GAP' WHERE id = ?",
				rejectedId);
		long expiredId = id(apply(applicant, recruitingMatch(host).getId(), AGREED));
		jdbcTemplate.update("UPDATE match_application SET status = 'EXPIRED' WHERE id = ?", expiredId);
		long canceledId = id(apply(applicant, recruitingMatch(host).getId(), AGREED));
		cancel(applicant, canceledId, null).andExpect(status().isOk());
		apply(applicant, recruitingMatch(host).getId(), AGREED).andExpect(status().isCreated());
		apply(activeUser(Gender.FEMALE), recruitingMatch(host).getId(), AGREED).andExpect(status().isCreated());

		mvc.perform(get("/api/v1/users/me/applications/closed").header("Authorization", bearer(applicant)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items.length()").value(3))
				.andExpect(jsonPath("$.result.items[0].applicationId").value(canceledId))
				.andExpect(jsonPath("$.result.items[0].applicationStatus").value("CANCELED"))
				.andExpect(jsonPath("$.result.items[1].applicationId").value(expiredId))
				.andExpect(jsonPath("$.result.items[2].applicationId").value(rejectedId))
				.andExpect(jsonPath("$.result.items[2].applicationStatus").value("REJECTED"))
				.andExpect(jsonPath("$.result.hasNext").value(false));
		mvc.perform(get("/api/v1/users/me/applications/closed").param("size", "2")
						.header("Authorization", bearer(applicant)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items.length()").value(2))
				.andExpect(jsonPath("$.result.hasNext").value(true))
				.andExpect(jsonPath("$.result.totalElements").value(3));
		mvc.perform(get("/api/v1/users/me/applications/closed").param("page", "4")
						.header("Authorization", bearer(applicant)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items.length()").value(0))
				.andExpect(jsonPath("$.result.hasNext").value(false))
				.andExpect(jsonPath("$.result.totalElements").value(3));
		mvc.perform(get("/api/v1/users/me/applications/closed").param("page", "10001")
						.header("Authorization", bearer(applicant)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
		mvc.perform(get("/api/v1/users/me/applications/closed"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void pendingDetailHasCommonInfoOnlyAndIsVisibleOnlyToApplicant() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host);
		User applicant = activeUser(Gender.FEMALE);
		long applicationId = id(apply(applicant, match.getId(), AGREED));

		detail(applicant, applicationId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.applicationId").value(applicationId))
				.andExpect(jsonPath("$.result.status").value("PENDING"))
				.andExpect(jsonPath("$.result.appliedAt").isString())
				.andExpect(jsonPath("$.result.refundPolicyAgreedAt").isString())
				.andExpect(jsonPath("$.result.match.matchId").value(match.getId()))
				.andExpect(jsonPath("$.result.match.matchTypes[0]").value("MIXED_DOUBLES"))
				.andExpect(jsonPath("$.result.match.participationFee").value(3500))
				.andExpect(jsonPath("$.result.match.startsAt").isString())
				.andExpect(jsonPath("$.result.match.endsAt").isString())
				.andExpect(jsonPath("$.result.match.court.name").value("장충테니스장"))
				.andExpect(jsonPath("$.result.match.court.courtNumber").value("1"))
				.andExpect(jsonPath("$.result.match.court.latitude").value(37.558651))
				.andExpect(jsonPath("$.result.match.court.hasParking").value(true))
				.andExpect(jsonPath("$.result.match.court.hasShower").value(true))
				.andExpect(jsonPath("$.result.host.userId").value(host.getId()))
				.andExpect(jsonPath("$.result.host.nickname").value(host.getNickname()))
				.andExpect(jsonPath("$.result.acceptedAt").doesNotExist())
				.andExpect(jsonPath("$.result.payment").doesNotExist())
				.andExpect(jsonPath("$.result.participants").doesNotExist())
				.andExpect(jsonPath("$.result.rejection").doesNotExist())
				.andExpect(jsonPath("$.result.cancellation").doesNotExist());

		detail(host, applicationId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
	}

	@Test
	void paymentPendingDetailIncludesPaymentInfoFromModelOnly() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);
		long applicationId = id(apply(applicant, match.getId(), AGREED));
		jdbcTemplate.update("UPDATE match_application SET status = 'PAYMENT_PENDING', accepted_at = now(), "
				+ "payment_due_at = now() + interval '1 day' WHERE id = ?", applicationId);

		detail(applicant, applicationId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("PAYMENT_PENDING"))
				.andExpect(jsonPath("$.result.acceptedAt").isString())
				.andExpect(jsonPath("$.result.payment.amount").value(3500))
				.andExpect(jsonPath("$.result.payment.paymentDueAt").isString())
				.andExpect(jsonPath("$.result.participants").doesNotExist())
				.andExpect(jsonPath("$.result.cancellation").doesNotExist());
	}

	@Test
	void confirmedDetailIncludesHostAndConfirmedParticipants() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host);
		User applicant = activeUser(Gender.FEMALE);
		User other = activeUser(Gender.FEMALE);
		User pending = activeUser(Gender.MALE);
		long applicationId = id(apply(applicant, match.getId(), AGREED));
		long otherId = id(apply(other, match.getId(), AGREED));
		apply(pending, match.getId(), AGREED).andExpect(status().isCreated());
		jdbcTemplate.update("UPDATE match_application SET status = 'CONFIRMED', accepted_at = now() WHERE id IN (?, ?)",
				applicationId, otherId);
		jdbcTemplate.update("UPDATE users SET profile_image_key = 'host.png' WHERE id = ?", host.getId());

		detail(applicant, applicationId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.result.host.profileImageUrl")
						.value("/api/v1/users/" + host.getId() + "/profile-image"))
				.andExpect(jsonPath("$.result.acceptedAt").isString())
				.andExpect(jsonPath("$.result.payment").doesNotExist())
				.andExpect(jsonPath("$.result.participants.length()").value(3))
				.andExpect(jsonPath("$.result.participants[0].userId").value(host.getId()))
				.andExpect(jsonPath("$.result.participants[0].host").value(true))
				.andExpect(jsonPath("$.result.participants[1].userId").value(applicant.getId()))
				.andExpect(jsonPath("$.result.participants[1].host").value(false))
				.andExpect(jsonPath("$.result.participants[1].profileImageUrl").value(nullValue()))
				.andExpect(jsonPath("$.result.participants[2].userId").value(other.getId()))
				.andExpect(jsonPath("$.result.participants[2].gender").value("FEMALE"));
	}

	@Test
	void rejectedDetailHidesRejectReason() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);
		long applicationId = id(apply(applicant, match.getId(), AGREED));
		jdbcTemplate.update("UPDATE match_application SET status = 'REJECTED', reject_reason = 'SKILL_GAP' WHERE id = ?",
				applicationId);

		detail(applicant, applicationId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("REJECTED"))
				.andExpect(jsonPath("$.result.rejection").doesNotExist())
				.andExpect(jsonPath("$.result.participants").doesNotExist());
	}

	@Test
	void cancelsPendingApplicationIdempotentlyAndAllowsReapplication() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);
		long applicationId = id(apply(applicant, match.getId(), AGREED));

		String canceledAt = json.readTree(cancel(applicant, applicationId, "{\"reason\":\"SCHEDULE_CONFLICT\"}")
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.result.status").value("CANCELED"))
						.andExpect(jsonPath("$.result.cancelReason").value("SCHEDULE_CONFLICT"))
						.andExpect(jsonPath("$.result.canceledAt").isString())
						.andReturn().getResponse().getContentAsString())
				.path("result").path("canceledAt").asText();
		cancel(applicant, applicationId, "{\"reason\":\"OTHER\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("CANCELED"))
				.andExpect(jsonPath("$.result.cancelReason").value("SCHEDULE_CONFLICT"))
				.andExpect(jsonPath("$.result.canceledAt").value(canceledAt));

		var row = jdbcTemplate.queryForMap("SELECT status, canceled_at, cancel_reason FROM match_application WHERE id = ?",
				applicationId);
		assertThat(row.get("status")).isEqualTo("CANCELED");
		assertThat(row.get("canceled_at")).isNotNull();
		assertThat(row.get("cancel_reason")).isEqualTo("SCHEDULE_CONFLICT");
		detail(applicant, applicationId)
				.andExpect(jsonPath("$.result.cancellation.reason").value("SCHEDULE_CONFLICT"))
				.andExpect(jsonPath("$.result.cancellation.canceledAt").value(canceledAt));

		long reappliedId = id(apply(applicant, match.getId(), AGREED).andExpect(status().isCreated()));
		assertThat(reappliedId).isNotEqualTo(applicationId);
		assertThat(applicationCount(match.getId())).isEqualTo(2);
	}

	@Test
	void cancelReasonIsOptionalAndMustBeKnownOption() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);
		long applicationId = id(apply(applicant, match.getId(), AGREED));

		cancel(applicant, applicationId, "{\"reason\":\"일정이 생겼어요\"}")
				.andExpect(status().isBadRequest());
		assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
		cancel(applicant, applicationId, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("CANCELED"))
				.andExpect(jsonPath("$.result.cancelReason").value(nullValue()));
	}

	@Test
	void blocksCancellationByOtherUserAndFromNonPendingStatus() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE));
		User applicant = activeUser(Gender.FEMALE);
		User stranger = activeUser(Gender.FEMALE);
		long applicationId = id(apply(applicant, match.getId(), AGREED));

		cancel(stranger, applicationId, null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");

		for (String status : List.of("PAYMENT_PENDING", "CONFIRMED", "REJECTED", "EXPIRED")) {
			jdbcTemplate.update("UPDATE match_application SET status = ? WHERE id = ?", status, applicationId);
			cancel(applicant, applicationId, null)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("APPLICATION_NOT_CANCELABLE"));
			assertThat(applicationStatus(applicationId)).isEqualTo(status);
		}
	}

	private ResultActions myApplications(User user, String... params) throws Exception {
		var request = get("/api/v1/users/me/applications").header("Authorization", bearer(user));
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return mvc.perform(request);
	}

	private ResultActions detail(User user, long applicationId) throws Exception {
		return mvc.perform(get("/api/v1/applications/{id}", applicationId).header("Authorization", bearer(user)));
	}

	private ResultActions apply(User user, Long matchId, String body) throws Exception {
		return mvc.perform(post("/api/v1/matches/{matchId}/applications", matchId)
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions cancel(User user, long applicationId, String body) throws Exception {
		var request = post("/api/v1/applications/{id}/cancel", applicationId).header("Authorization", bearer(user));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mvc.perform(request);
	}

	private long id(ResultActions result) throws Exception {
		return json.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
				.path("result").path("applicationId").asLong();
	}

	private String bearer(User user) {
		return "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
	}

	private User activeUser(Gender gender) {
		return saveUser(EntityFixtures.user(entityManager, nickname(), gender));
	}

	private User saveUser(User user) {
		User saved = userRepository.save(user);
		userIds.add(saved.getId());
		return saved;
	}

	private User onboarded(Gender gender, BigDecimal ntrp, AgeGroup ageGroup) {
		Area area = entityManager.createQuery("select a from Area a where a.code = '11440'", Area.class)
				.getSingleResult();
		User user = User.signUp();
		user.completeOnboarding(new OnboardingProfile(nickname(), gender, ageGroup,
				ExperiencePeriod.ONE_TO_THREE_YEARS, ntrp, false, Set.of(PreferredMatchType.SINGLES), Set.of(area),
				Set.of(PreferredTimeSlot.WEEKEND_MORNING)));
		return user;
	}

	private Match recruitingMatch(User host) {
		return recruitingMatch(host, LocalDateTime.now(clock).plusDays(3).truncatedTo(ChronoUnit.HOURS));
	}

	private Match recruitingMatch(User host, LocalDateTime startsAt) {
		Match match = match(host, Set.of(MatchType.MIXED_DOUBLES), 1, 2, LocalDateTime.now(clock).plusDays(1), startsAt);
		recruit(match);
		return match;
	}

	private Match match(User host, Set<MatchType> matchTypes, int maleRecruitCount, int femaleRecruitCount,
			LocalDateTime recruitmentDeadline) {
		return match(host, matchTypes, maleRecruitCount, femaleRecruitCount, recruitmentDeadline,
				LocalDateTime.now(clock).plusDays(3).truncatedTo(ChronoUnit.HOURS));
	}

	private Match match(User host, Set<MatchType> matchTypes, int maleRecruitCount, int femaleRecruitCount,
			LocalDateTime recruitmentDeadline, LocalDateTime startsAt) {
		Court court = courtRepository.save(EntityFixtures.court());
		courtIds.add(court.getId());
		CourtSlot courtSlot = courtSlotRepository.save(EntityFixtures.courtSlot(court, host, "1", startsAt));
		Match match = matchRepository.save(Match.builder()
				.courtSlot(courtSlot)
				.host(host)
				.matchTypes(matchTypes)
				.maleRecruitCount(maleRecruitCount)
				.femaleRecruitCount(femaleRecruitCount)
				.minNtrp(new BigDecimal("2.5"))
				.maxNtrp(new BigDecimal("3.5"))
				.participationFee(3500)
				.recruitmentDeadline(recruitmentDeadline)
				.preferredAgeGroups(Set.of(AgeGroup.TWENTIES))
				.build());
		matchIds.add(match.getId());
		return match;
	}

	private void reschedule(Match match, LocalDateTime startsAt) {
		jdbcTemplate.update("UPDATE court_slot SET starts_at = ?, ends_at = ? WHERE id = ?", startsAt,
				startsAt.plusHours(2), match.getCourtSlot().getId());
	}

	private void recruit(Match match) {
		jdbcTemplate.update("UPDATE match SET status = 'RECRUITING' WHERE id = ?", match.getId());
	}

	private int applicationCount(Long matchId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM match_application WHERE match_id = ?",
				Integer.class, matchId);
	}

	private String applicationStatus(long applicationId) {
		return jdbcTemplate.queryForObject("SELECT status FROM match_application WHERE id = ?", String.class,
				applicationId);
	}

	private void awaitBlockedOnLock() throws Exception {
		try (Connection connection = DriverManager.getConnection(dataSource.getJdbcUrl(), dataSource.getUsername(),
				dataSource.getPassword())) {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (System.nanoTime() < deadline) {
				try (ResultSet result = connection.createStatement()
						.executeQuery("SELECT count(*) FROM pg_locks WHERE NOT granted")) {
					result.next();
					if (result.getInt(1) > 0) {
						return;
					}
				}
				Thread.sleep(20);
			}
		}
		throw new AssertionError("Application insert did not wait on the active application lock");
	}

	private static String nickname() {
		return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
	}
}
