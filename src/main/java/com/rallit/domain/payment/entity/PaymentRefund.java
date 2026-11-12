package com.rallit.domain.payment.entity;

import java.time.LocalDateTime;

import com.rallit.domain.payment.enums.RefundStatus;
import com.rallit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payment_refund")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRefund extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id", nullable = false)
	private Payment payment;

	@Column(nullable = false, unique = true, length = 300)
	private String idempotencyKey;

	@Column(nullable = false)
	private Integer amount;

	@Column(nullable = false, length = 200)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RefundStatus status;

	@Column(length = 200)
	private String transactionKey;

	private LocalDateTime canceledAt;

	private LocalDateTime lastRequestedAt;

	private PaymentRefund(Payment payment, String idempotencyKey, Integer amount, String reason) {
		this.payment = payment;
		this.idempotencyKey = idempotencyKey;
		this.amount = amount;
		this.reason = reason;
		this.status = RefundStatus.REQUESTED;
	}

	public static PaymentRefund request(Payment payment, String idempotencyKey, Integer amount, String reason) {
		return new PaymentRefund(payment, idempotencyKey, amount, reason);
	}
}
