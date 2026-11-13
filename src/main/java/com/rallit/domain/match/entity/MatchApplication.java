package com.rallit.domain.match.entity;

import java.time.LocalDateTime;

import com.rallit.domain.match.enums.MatchApplicationCancelReason;
import com.rallit.domain.match.enums.MatchApplicationRejectReason;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.exception.MatchApplicationErrorStatus;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.global.apiPayload.exception.GeneralException;
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
@Table(name = "match_application")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchApplication extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "match_id", nullable = false)
	private Match match;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "applicant_user_id", nullable = false)
	private User applicant;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Gender applicantGender;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MatchApplicationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private MatchApplicationRejectReason rejectReason;

	@Enumerated(EnumType.STRING)
	@Column(length = 500)
	private MatchApplicationCancelReason cancelReason;

	@Column(nullable = false)
	private LocalDateTime refundPolicyAgreedAt;

	private LocalDateTime acceptedAt;

	private LocalDateTime paymentDueAt;

	private LocalDateTime canceledAt;

	private MatchApplication(Match match, User applicant, LocalDateTime refundPolicyAgreedAt) {
		this.match = match;
		this.applicant = applicant;
		this.applicantGender = applicant.getGender();
		this.status = MatchApplicationStatus.PENDING;
		this.refundPolicyAgreedAt = refundPolicyAgreedAt;
	}

	public static MatchApplication create(Match match, User applicant, LocalDateTime refundPolicyAgreedAt) {
		return new MatchApplication(match, applicant, refundPolicyAgreedAt);
	}

	public void cancel(MatchApplicationCancelReason cancelReason, LocalDateTime canceledAt) {
		if (status == MatchApplicationStatus.CANCELED) {
			return;
		}
		if (status != MatchApplicationStatus.PENDING) {
			throw new GeneralException(MatchApplicationErrorStatus.APPLICATION_NOT_CANCELABLE);
		}
		this.status = MatchApplicationStatus.CANCELED;
		this.cancelReason = cancelReason;
		this.canceledAt = canceledAt;
	}
}
