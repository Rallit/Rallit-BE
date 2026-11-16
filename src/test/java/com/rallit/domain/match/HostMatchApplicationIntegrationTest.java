package com.rallit.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.rallit.domain.match.enums.MatchApplicationRejectReason;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.exception.MatchApplicationErrorStatus;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.match.service.MatchApplicationService;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.Gender;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class HostMatchApplicationIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mvc;

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

	private final ExecutorService executor = Executors.newFixedThreadPool(4);

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
	void hostSeesPendingApplicantsAndSeatOccupyingParticipantsIncludingHost() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 2);
		User paymentPending = activeUser(Gender.FEMALE);
		long paymentPendingId = apply(paymentPending, match);
		long confirmedId = apply(activeUser(Gender.FEMALE), match);
		User pendingMale = activeUser(Gender.MALE);
		long pendingMaleId = apply(pendingMale, match);
		long pendingFemaleId = apply(activeUser(Gender.FEMALE), match);
		long canceledId = apply(activeUser(Gender.FEMALE), match);
		long rejectedId = apply(activeUser(Gender.MALE), match);
		accept(host, match, paymentPendingId).andExpect(status().isOk());
		jdbcTemplate.update("UPDATE match_application SET status = 'CONFIRMED', accepted_at = now() WHERE id = ?",
				confirmedId);
		matchApplicationService.cancel(userIdOf(canceledId), canceledId, null);
		reject(host, match, rejectedId, "{\"reason\":\"SKILL_GAP\"}").andExpect(status().isOk());

		hostApplications(host, match)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.match.matchId").value(match.getId()))
				.andExpect(jsonPath("$.result.match.status").value("RECRUITING"))
				.andExpect(jsonPath("$.result.match.matchTypes[0]").value("MIXED_DOUBLES"))
				.andExpect(jsonPath("$.result.match.courtName").value("장충테니스장"))
				.andExpect(jsonPath("$.result.match.courtNumber").value("1"))
				.andExpect(jsonPath("$.result.match.courtSlotStatus").doesNotExist())
				.andExpect(jsonPath("$.result.match.startsAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
				.andExpect(jsonPath("$.result.recruitment.capacity").value(4))
				.andExpect(jsonPath("$.result.recruitment.currentParticipantCount").value(3))
				.andExpect(jsonPath("$.result.recruitment.pendingCount").value(2))
				.andExpect(jsonPath("$.result.recruitment.male.recruitCount").value(1))
				.andExpect(jsonPath("$.result.recruitment.male.occupiedCount").value(0))
				.andExpect(jsonPath("$.result.recruitment.male.remainingCount").value(1))
				.andExpect(jsonPath("$.result.recruitment.female.recruitCount").value(2))
				.andExpect(jsonPath("$.result.recruitment.female.occupiedCount").value(2))
				.andExpect(jsonPath("$.result.recruitment.female.remainingCount").value(0))
				.andExpect(jsonPath("$.result.recruitment.recruitmentFilled").value(false))
				.andExpect(jsonPath("$.result.pendingApplications.length()").value(2))
				.andExpect(jsonPath("$.result.pendingApplications[0].applicationId").value(pendingMaleId))
				.andExpect(jsonPath("$.result.pendingApplications[0].appliedAt").isString())
				.andExpect(jsonPath("$.result.pendingApplications[0].applicant.userId").value(pendingMale.getId()))
				.andExpect(jsonPath("$.result.pendingApplications[0].applicant.nickname")
						.value(pendingMale.getNickname()))
				.andExpect(jsonPath("$.result.pendingApplications[0].applicant.gender").value("MALE"))
				.andExpect(jsonPath("$.result.pendingApplications[0].applicant.ntrp").value(3.0))
				.andExpect(jsonPath("$.result.pendingApplications[0].applicant.ageGroup").value("TWENTIES"))
				.andExpect(jsonPath("$.result.pendingApplications[0].applicant.experiencePeriod")
						.value("ONE_TO_THREE_YEARS"))
				.andExpect(jsonPath("$.result.pendingApplications[1].applicationId").value(pendingFemaleId))
				.andExpect(jsonPath("$.result.participants.length()").value(3))
				.andExpect(jsonPath("$.result.participants[0].host").value(true))
				.andExpect(jsonPath("$.result.participants[0].user.userId").value(host.getId()))
				.andExpect(jsonPath("$.result.participants[0].applicationId").doesNotExist())
				.andExpect(jsonPath("$.result.participants[1].host").value(false))
				.andExpect(jsonPath("$.result.participants[1].applicationId").value(paymentPendingId))
				.andExpect(jsonPath("$.result.participants[1].status").value("PAYMENT_PENDING"))
				.andExpect(jsonPath("$.result.participants[1].paymentDueAt").isString())
				.andExpect(jsonPath("$.result.participants[1].user.userId").value(paymentPending.getId()))
				.andExpect(jsonPath("$.result.participants[2].applicationId").value(confirmedId))
				.andExpect(jsonPath("$.result.participants[2].status").value("CONFIRMED"));
	}

	@Test
	void onlyHostCanViewApplicationManagement() throws Exception {
		Match match = recruitingMatch(activeUser(Gender.MALE), 1, 2);
		apply(activeUser(Gender.FEMALE), match);

		hostApplications(activeUser(Gender.FEMALE), match)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_MATCH_HOST"));
		mvc.perform(get("/api/v1/host/matches/{matchId}/applications", Long.MAX_VALUE)
						.header("Authorization", bearer(activeUser(Gender.MALE))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));
		mvc.perform(get("/api/v1/host/matches/{matchId}/applications", match.getId()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void acceptMovesPendingToPaymentPendingWithPaymentDueAtAfterTwentyFourHours() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 2);
		long applicationId = apply(activeUser(Gender.FEMALE), match);
		LocalDateTime before = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

		accept(host, match, applicationId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.applicationId").value(applicationId))
				.andExpect(jsonPath("$.result.status").value("PAYMENT_PENDING"))
				.andExpect(jsonPath("$.result.acceptedAt").isString())
				.andExpect(jsonPath("$.result.paymentDueAt").isString());

		Map<String, Object> row = applicationRow(applicationId);
		LocalDateTime acceptedAt = ((Timestamp) row.get("accepted_at")).toLocalDateTime();
		assertThat(row.get("status")).isEqualTo("PAYMENT_PENDING");
		assertThat(acceptedAt).isAfterOrEqualTo(before).isBeforeOrEqualTo(LocalDateTime.now(clock));
		assertThat(((Timestamp) row.get("payment_due_at")).toLocalDateTime()).isEqualTo(acceptedAt.plusHours(24));
		assertThat(matchStatus(match)).isEqualTo("RECRUITING");
	}

	@Test
	void paymentDueAtDoesNotPassFinalDeadlineOneHourBeforeMatchStart() throws Exception {
		User host = activeUser(Gender.MALE);
		LocalDateTime startsAt = LocalDateTime.now(clock).plusHours(10).truncatedTo(ChronoUnit.SECONDS);
		Match match = match(host, 1, 2, LocalDateTime.now(clock).plusHours(5), startsAt);
		recruit(match);
		long applicationId = apply(activeUser(Gender.FEMALE), match);

		accept(host, match, applicationId).andExpect(status().isOk());

		assertThat(((Timestamp) applicationRow(applicationId).get("payment_due_at")).toLocalDateTime())
				.isEqualTo(startsAt.minusHours(1));
	}

	@Test
	void pendingApplicationsDoNotOccupySeatsButPaymentPendingAndConfirmedDo() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 2, 1);
		long firstFemaleId = apply(activeUser(Gender.FEMALE), match);
		long secondFemaleId = apply(activeUser(Gender.FEMALE), match);
		long confirmedMaleId = apply(activeUser(Gender.MALE), match);
		long secondMaleId = apply(activeUser(Gender.MALE), match);
		long thirdMaleId = apply(activeUser(Gender.MALE), match);
		jdbcTemplate.update("UPDATE match_application SET status = 'CONFIRMED' WHERE id = ?", confirmedMaleId);

		accept(host, match, firstFemaleId).andExpect(status().isOk());
		accept(host, match, secondFemaleId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
		accept(host, match, secondMaleId).andExpect(status().isOk());
		accept(host, match, thirdMaleId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));

		assertThat(applicationStatus(secondFemaleId)).isEqualTo("PENDING");
		assertThat(applicationStatus(thirdMaleId)).isEqualTo("PENDING");
		assertThat(matchStatus(match)).isEqualTo("RECRUITING");
		hostApplications(host, match)
				.andExpect(jsonPath("$.result.recruitment.capacity").value(4))
				.andExpect(jsonPath("$.result.recruitment.currentParticipantCount").value(4))
				.andExpect(jsonPath("$.result.recruitment.recruitmentFilled").value(true))
				.andExpect(jsonPath("$.result.match.status").value("RECRUITING"));
	}

	@Test
	void rejectStoresReasonAndRejectedApplicationOccupiesNoSeat() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 1);
		User rejected = activeUser(Gender.FEMALE);
		long rejectedId = apply(rejected, match);
		long acceptedId = apply(activeUser(Gender.FEMALE), match);

		reject(host, match, rejectedId, "{\"reason\":\"COMPOSITION_MISMATCH\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.applicationId").value(rejectedId))
				.andExpect(jsonPath("$.result.status").value("REJECTED"))
				.andExpect(jsonPath("$.result.rejectReason").value("COMPOSITION_MISMATCH"));
		accept(host, match, acceptedId).andExpect(status().isOk());

		Map<String, Object> row = applicationRow(rejectedId);
		assertThat(row.get("status")).isEqualTo("REJECTED");
		assertThat(row.get("reject_reason")).isEqualTo("COMPOSITION_MISMATCH");
		assertThat(row.get("accepted_at")).isNull();
		assertThat(row.get("payment_due_at")).isNull();
		mvc.perform(get("/api/v1/applications/{id}", rejectedId).header("Authorization", bearer(rejected)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("REJECTED"))
				.andExpect(jsonPath("$.result.rejectReason").doesNotExist());
	}

	@Test
	void rejectReasonIsOptionalButMustBeKnownOption() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 2);
		long invalidReasonId = apply(activeUser(Gender.FEMALE), match);
		long emptyReasonId = apply(activeUser(Gender.FEMALE), match);
		long noBodyId = apply(activeUser(Gender.MALE), match);

		reject(host, match, invalidReasonId, "{\"reason\":\"실력 차이\"}")
				.andExpect(status().isBadRequest());
		assertThat(applicationStatus(invalidReasonId)).isEqualTo("PENDING");
		reject(host, match, emptyReasonId, "{}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("REJECTED"))
				.andExpect(jsonPath("$.result.rejectReason").value(org.hamcrest.Matchers.nullValue()));
		mvc.perform(post("/api/v1/host/matches/{matchId}/applications/{id}/reject", match.getId(), noBodyId)
						.header("Authorization", bearer(host)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("REJECTED"));

		assertThat(applicationStatus(emptyReasonId)).isEqualTo("REJECTED");
		assertThat(rejectReason(emptyReasonId)).isNull();
		assertThat(applicationStatus(noBodyId)).isEqualTo("REJECTED");
	}

	@Test
	void blocksNonHostAndApplicationOfAnotherMatch() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 2);
		User applicant = activeUser(Gender.FEMALE);
		long applicationId = apply(applicant, match);
		User otherHost = activeUser(Gender.MALE);
		Match otherMatch = recruitingMatch(otherHost, 1, 2);

		for (User user : List.of(applicant, otherHost)) {
			accept(user, match, applicationId)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("NOT_MATCH_HOST"));
			reject(user, match, applicationId, "{\"reason\":\"OTHER\"}")
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("NOT_MATCH_HOST"));
		}
		accept(otherHost, otherMatch, applicationId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		reject(otherHost, otherMatch, applicationId, "{\"reason\":\"OTHER\"}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		mvc.perform(post("/api/v1/host/matches/{matchId}/applications/{id}/accept", match.getId(), applicationId))
				.andExpect(status().isUnauthorized());

		assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
	}

	@Test
	void processedApplicationCannotBeAcceptedOrRejectedAgain() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 2);
		long acceptedId = apply(activeUser(Gender.FEMALE), match);
		long rejectedId = apply(activeUser(Gender.FEMALE), match);
		accept(host, match, acceptedId).andExpect(status().isOk());
		reject(host, match, rejectedId, "{\"reason\":\"SKILL_GAP\"}").andExpect(status().isOk());
		Map<String, Object> acceptedRow = applicationRow(acceptedId);

		accept(host, match, acceptedId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_PENDING"));
		reject(host, match, acceptedId, "{\"reason\":\"OTHER\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_PENDING"));
		accept(host, match, rejectedId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_PENDING"));
		reject(host, match, rejectedId, "{\"reason\":\"OTHER\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_PENDING"));
		for (String status : List.of("CONFIRMED", "CANCELED", "EXPIRED")) {
			jdbcTemplate.update("UPDATE match_application SET status = ? WHERE id = ?", status, rejectedId);
			accept(host, match, rejectedId)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("APPLICATION_NOT_PENDING"));
			assertThat(applicationStatus(rejectedId)).isEqualTo(status);
		}

		assertThat(applicationRow(acceptedId)).isEqualTo(acceptedRow);
		assertThat(rejectReason(rejectedId)).isEqualTo("SKILL_GAP");
		assertThat(occupiedSeats(match)).isEqualTo(1);
	}

	@Test
	void acceptRequiresRecruitingMatchBeforeDeadline() throws Exception {
		User host = activeUser(Gender.MALE);
		Match pendingApproval = match(host, 1, 2, LocalDateTime.now(clock).plusDays(1),
				LocalDateTime.now(clock).plusDays(3));
		Match closed = recruitingMatch(host, 1, 2);
		long pendingApprovalApplicationId = insertPendingApplication(pendingApproval, activeUser(Gender.FEMALE));
		long closedApplicationId = apply(activeUser(Gender.FEMALE), closed);
		jdbcTemplate.update("UPDATE match SET recruitment_deadline = ? WHERE id = ?",
				LocalDateTime.now(clock).minusMinutes(1), closed.getId());

		accept(host, pendingApproval, pendingApprovalApplicationId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MATCH_NOT_RECRUITING"));
		accept(host, closed, closedApplicationId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MATCH_RECRUITMENT_CLOSED"));
		mvc.perform(post("/api/v1/host/matches/{matchId}/applications/{id}/accept", Long.MAX_VALUE, closedApplicationId)
						.header("Authorization", bearer(host)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));

		assertThat(applicationStatus(pendingApprovalApplicationId)).isEqualTo("PENDING");
		assertThat(applicationStatus(closedApplicationId)).isEqualTo("PENDING");
	}

	@Test
	void acceptIsBlockedAfterFinalDeadlineOneHourBeforeMatchStart() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 2);
		long applicationId = apply(activeUser(Gender.FEMALE), match);
		LocalDateTime startsAt = LocalDateTime.now(clock).plusMinutes(59);
		jdbcTemplate.update("""
				UPDATE court_slot SET starts_at = ?, ends_at = ?
				WHERE id = (SELECT court_slot_id FROM match WHERE id = ?)
				""", startsAt, startsAt.plusHours(2), match.getId());

		accept(host, match, applicationId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MATCH_RECRUITMENT_CLOSED"));

		assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
	}

	@Test
	void secondAcceptWaitsForMatchLockAndFailsWhenLastSeatIsTaken() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 1);
		long firstId = apply(activeUser(Gender.FEMALE), match);
		long secondId = apply(activeUser(Gender.FEMALE), match);
		CountDownLatch accepted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
				status -> {
					matchApplicationService.accept(host.getId(), match.getId(), firstId);
					accepted.countDown();
					await(release);
				}));
		assertThat(accepted.await(30, TimeUnit.SECONDS)).isTrue();
		Future<Throwable> second = executor.submit(
				() -> catchThrowable(() -> matchApplicationService.accept(host.getId(), match.getId(), secondId)));
		awaitBlockedOnLock();
		assertThat(second).isNotDone();
		release.countDown();
		holder.get(30, TimeUnit.SECONDS);

		assertThat(second.get(30, TimeUnit.SECONDS)).isInstanceOfSatisfying(GeneralException.class,
				exception -> assertThat(exception.getCode()).isEqualTo(MatchApplicationErrorStatus.SEAT_UNAVAILABLE));
		assertThat(applicationStatus(firstId)).isEqualTo("PAYMENT_PENDING");
		assertThat(applicationStatus(secondId)).isEqualTo("PENDING");
		assertThat(occupiedSeats(match)).isEqualTo(1);
	}

	@Test
	void concurrentAcceptsForLastSeatAllowOnlyOne() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 1);
		List<Long> applicationIds = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			applicationIds.add(apply(activeUser(Gender.FEMALE), match));
		}
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Throwable>> results = new ArrayList<>();
		for (Long applicationId : applicationIds) {
			results.add(executor.submit(() -> {
				start.await();
				return catchThrowable(() -> matchApplicationService.accept(host.getId(), match.getId(), applicationId));
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

		assertThat(failures).hasSize(3).allSatisfy(failure -> assertThat(failure)
				.isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
						.isEqualTo(MatchApplicationErrorStatus.SEAT_UNAVAILABLE)));
		assertThat(occupiedSeats(match)).isEqualTo(1);
		assertThat(applicationIds.stream().map(this::applicationStatus).toList())
				.containsExactlyInAnyOrder("PAYMENT_PENDING", "PENDING", "PENDING", "PENDING");
	}

	@Test
	void rejectWaitingOnAcceptOfSameApplicationSeesAcceptedState() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 2);
		long applicationId = apply(activeUser(Gender.FEMALE), match);
		CountDownLatch accepted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
				status -> {
					matchApplicationService.accept(host.getId(), match.getId(), applicationId);
					accepted.countDown();
					await(release);
				}));
		assertThat(accepted.await(30, TimeUnit.SECONDS)).isTrue();
		Future<Throwable> rejecting = executor.submit(() -> catchThrowable(() -> matchApplicationService.reject(
				host.getId(), match.getId(), applicationId, MatchApplicationRejectReason.OTHER)));
		awaitBlockedOnLock();
		release.countDown();
		holder.get(30, TimeUnit.SECONDS);

		assertThat(rejecting.get(30, TimeUnit.SECONDS)).isInstanceOfSatisfying(GeneralException.class,
				exception -> assertThat(exception.getCode())
						.isEqualTo(MatchApplicationErrorStatus.APPLICATION_NOT_PENDING));
		assertThat(applicationStatus(applicationId)).isEqualTo("PAYMENT_PENDING");
		assertThat(rejectReason(applicationId)).isNull();
	}

	@Test
	void acceptWaitingOnApplicantCancelSeesCanceledStateAndOccupiesNoSeat() throws Exception {
		User host = activeUser(Gender.MALE);
		Match match = recruitingMatch(host, 1, 2);
		User applicant = activeUser(Gender.FEMALE);
		long applicationId = apply(applicant, match);
		CountDownLatch canceled = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
				status -> {
					matchApplicationService.cancel(applicant.getId(), applicationId, null);
					canceled.countDown();
					await(release);
				}));
		assertThat(canceled.await(30, TimeUnit.SECONDS)).isTrue();
		Future<Throwable> accepting = executor.submit(
				() -> catchThrowable(() -> matchApplicationService.accept(host.getId(), match.getId(), applicationId)));
		awaitBlockedOnLock();
		release.countDown();
		holder.get(30, TimeUnit.SECONDS);

		assertThat(accepting.get(30, TimeUnit.SECONDS)).isInstanceOfSatisfying(GeneralException.class,
				exception -> assertThat(exception.getCode())
						.isEqualTo(MatchApplicationErrorStatus.APPLICATION_NOT_PENDING));
		assertThat(applicationStatus(applicationId)).isEqualTo("CANCELED");
		assertThat(occupiedSeats(match)).isZero();
	}

	private ResultActions hostApplications(User user, Match match) throws Exception {
		return mvc.perform(get("/api/v1/host/matches/{matchId}/applications", match.getId())
				.header("Authorization", bearer(user)));
	}

	private ResultActions accept(User user, Match match, long applicationId) throws Exception {
		return mvc.perform(post("/api/v1/host/matches/{matchId}/applications/{id}/accept", match.getId(), applicationId)
				.header("Authorization", bearer(user)));
	}

	private ResultActions reject(User user, Match match, long applicationId, String body) throws Exception {
		return mvc.perform(post("/api/v1/host/matches/{matchId}/applications/{id}/reject", match.getId(), applicationId)
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private long apply(User applicant, Match match) {
		return matchApplicationService.apply(applicant.getId(), match.getId()).applicationId();
	}

	private long insertPendingApplication(Match match, User applicant) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO match_application (match_id, applicant_user_id, applicant_gender, status,
				    refund_policy_agreed_at, created_at, updated_at)
				VALUES (?, ?, ?, 'PENDING', now(), now(), now())
				RETURNING id
				""", Long.class, match.getId(), applicant.getId(), applicant.getGender().name());
	}

	private String bearer(User user) {
		return "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
	}

	private User activeUser(Gender gender) {
		User saved = userRepository.save(EntityFixtures.user(entityManager, nickname(), gender));
		userIds.add(saved.getId());
		return saved;
	}

	private Match recruitingMatch(User host, int maleRecruitCount, int femaleRecruitCount) {
		Match match = match(host, maleRecruitCount, femaleRecruitCount, LocalDateTime.now(clock).plusDays(1),
				LocalDateTime.now(clock).plusDays(3).truncatedTo(ChronoUnit.HOURS));
		recruit(match);
		return match;
	}

	private Match match(User host, int maleRecruitCount, int femaleRecruitCount, LocalDateTime recruitmentDeadline,
			LocalDateTime startsAt) {
		Court court = courtRepository.save(EntityFixtures.court());
		courtIds.add(court.getId());
		CourtSlot courtSlot = courtSlotRepository.save(EntityFixtures.courtSlot(court, host, "1", startsAt));
		Match match = matchRepository.save(Match.builder()
				.courtSlot(courtSlot)
				.host(host)
				.matchTypes(Set.of(MatchType.MIXED_DOUBLES))
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

	private void recruit(Match match) {
		jdbcTemplate.update("UPDATE match SET status = 'RECRUITING' WHERE id = ?", match.getId());
	}

	private Long userIdOf(long applicationId) {
		return jdbcTemplate.queryForObject("SELECT applicant_user_id FROM match_application WHERE id = ?",
				Long.class, applicationId);
	}

	private Map<String, Object> applicationRow(long applicationId) {
		return jdbcTemplate.queryForMap("SELECT * FROM match_application WHERE id = ?", applicationId);
	}

	private String applicationStatus(long applicationId) {
		return jdbcTemplate.queryForObject("SELECT status FROM match_application WHERE id = ?", String.class,
				applicationId);
	}

	private String rejectReason(long applicationId) {
		return jdbcTemplate.queryForObject("SELECT reject_reason FROM match_application WHERE id = ?", String.class,
				applicationId);
	}

	private String matchStatus(Match match) {
		return jdbcTemplate.queryForObject("SELECT status FROM match WHERE id = ?", String.class, match.getId());
	}

	private int occupiedSeats(Match match) {
		return jdbcTemplate.queryForObject("""
				SELECT count(*) FROM match_application
				WHERE match_id = ? AND status IN ('PAYMENT_PENDING', 'CONFIRMED')
				""", Integer.class, match.getId());
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
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
		throw new AssertionError("Second transaction did not wait on the row lock held by the first transaction");
	}

	private static String nickname() {
		return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
	}
}
