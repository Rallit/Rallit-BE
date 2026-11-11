package com.rallit.domain.payment.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.payment.enums.PaymentStatus;
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
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "match_application_id", nullable = false)
	private MatchApplication matchApplication;

	@Column(nullable = false, unique = true, length = 64)
	private String orderId;

	@Column(unique = true, length = 200)
	private String paymentKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentStatus status;

	@Column(nullable = false)
	private Integer amount;

	@Column(nullable = false)
	private Integer balanceAmount;

	@Column(length = 50)
	private String method;

	private LocalDateTime approvedAt;

	private LocalDateTime processingStartedAt;

	private Payment(MatchApplication matchApplication, Integer amount) {
		this.matchApplication = matchApplication;
		this.orderId = UUID.randomUUID().toString();
		this.status = PaymentStatus.READY;
		this.amount = amount;
		this.balanceAmount = amount;
	}

	public static Payment create(MatchApplication matchApplication, Integer amount) {
		return new Payment(matchApplication, amount);
	}
}
