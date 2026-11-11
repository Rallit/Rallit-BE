package com.rallit.domain.payment.repository;

import static com.rallit.support.EntityFixtures.MATCH_STARTS_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.domain.court.repository.CourtSlotRepository;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.repository.MatchApplicationRepository;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.payment.entity.Payment;
import com.rallit.domain.payment.entity.PaymentRefund;
import com.rallit.domain.payment.enums.PaymentStatus;
import com.rallit.domain.payment.enums.RefundStatus;
import com.rallit.domain.settlement.entity.Settlement;
import com.rallit.domain.settlement.enums.SettlementStatus;
import com.rallit.domain.settlement.repository.SettlementRepository;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.support.EntityFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourtRepository courtRepository;

	@Autowired
	private CourtSlotRepository courtSlotRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private MatchApplicationRepository matchApplicationRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentRefundRepository paymentRefundRepository;

	@Autowired
	private SettlementRepository settlementRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Match match;

	private MatchApplication application;

	@BeforeEach
	void setUp() {
		User host = userRepository.save(EntityFixtures.user("호스트", Gender.MALE));
		User applicant = userRepository.save(EntityFixtures.user("참가자", Gender.FEMALE));
		CourtSlot courtSlot = courtSlotRepository.save(
				EntityFixtures.courtSlot(courtRepository.save(EntityFixtures.court()), host, "3", MATCH_STARTS_AT));
		match = matchRepository.save(EntityFixtures.match(courtSlot, host));
		application = matchApplicationRepository.save(EntityFixtures.matchApplication(match, applicant));
	}

	@Test
	void savesReadyPaymentWithGeneratedOrderId() {
		Payment payment = paymentRepository.saveAndFlush(payment(3500));
		entityManager.clear();

		Payment found = paymentRepository.findById(payment.getId()).orElseThrow();

		assertThat(found.getStatus()).isEqualTo(PaymentStatus.READY);
		assertThat(found.getOrderId()).isNotBlank();
		assertThat(found.getBalanceAmount()).isEqualTo(3500);
		assertThat(found.getMatchApplication().getId()).isEqualTo(application.getId());
	}

	@Test
	void rejectsSecondActivePaymentForApplication() {
		paymentRepository.saveAndFlush(payment(3500));

		assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment(3500)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_payment_active_match_application");
	}

	@Test
	void allowsRetryAfterFailedPayment() {
		Payment failed = paymentRepository.saveAndFlush(payment(3500));
		jdbcTemplate.update("UPDATE payment SET status = 'FAILED' WHERE id = ?", failed.getId());

		paymentRepository.saveAndFlush(payment(3500));

		assertThat(paymentRepository.count()).isEqualTo(2);
	}

	@Test
	void mapsUnknownPaymentStatus() {
		Payment payment = paymentRepository.saveAndFlush(payment(3500));
		jdbcTemplate.update("UPDATE payment SET status = 'UNKNOWN' WHERE id = ?", payment.getId());
		entityManager.clear();

		assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
				.isEqualTo(PaymentStatus.UNKNOWN);
	}

	@Test
	void rejectsBalanceAmountOverPaymentAmount() {
		Payment payment = paymentRepository.saveAndFlush(payment(3500));

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE payment SET balance_amount = 4000 WHERE id = ?", payment.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_payment_balance_amount");
	}

	@Test
	void rejectsSecondPendingRefundForPayment() {
		Payment payment = paymentRepository.saveAndFlush(payment(3500));
		paymentRefundRepository.saveAndFlush(refund(payment, "refund-1"));

		assertThatThrownBy(() -> paymentRefundRepository.saveAndFlush(refund(payment, "refund-2")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_payment_refund_pending_payment");
	}

	@Test
	void mapsUnknownRefundStatus() {
		PaymentRefund refund = paymentRefundRepository.saveAndFlush(
				refund(paymentRepository.save(payment(3500)), "refund-1"));
		assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
		jdbcTemplate.update("UPDATE payment_refund SET status = 'UNKNOWN' WHERE id = ?", refund.getId());
		entityManager.clear();

		assertThat(paymentRefundRepository.findById(refund.getId()).orElseThrow().getStatus())
				.isEqualTo(RefundStatus.UNKNOWN);
	}

	@Test
	void rejectsDuplicateSettlementForMatch() {
		Settlement settlement = settlementRepository.saveAndFlush(settlement());
		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);

		assertThatThrownBy(() -> settlementRepository.saveAndFlush(settlement()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_settlement_match_id");
	}

	private Payment payment(int amount) {
		return Payment.create(application, amount);
	}

	private PaymentRefund refund(Payment payment, String idempotencyKey) {
		return PaymentRefund.request(payment, idempotencyKey, 1050, "참가자 취소");
	}

	private Settlement settlement() {
		return Settlement.create(match, 10500);
	}
}
