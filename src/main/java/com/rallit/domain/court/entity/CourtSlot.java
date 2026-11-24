package com.rallit.domain.court.entity;

import java.time.Instant;

import com.rallit.domain.court.enums.EvidenceReviewStatus;
import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.user.entity.User;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "court_slot")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourtSlot extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "court_id", nullable = false)
	private Court court;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "registrant_user_id", nullable = false)
	private User registrant;

	@Column(nullable = false, length = 20)
	private String courtNumber;

	@Column(nullable = false)
	private Instant startsAt;

	@Column(nullable = false)
	private Instant endsAt;

	@Column(length = 500)
	private String evidenceFileKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EvidenceReviewStatus evidenceReviewStatus;

	@Column(length = 500)
	private String rejectionReason;

	private Instant reviewedAt;

	@Builder
	private CourtSlot(Court court, User registrant, String courtNumber, Instant startsAt, Instant endsAt,
			String evidenceFileKey) {
		this.court = court;
		this.registrant = registrant;
		this.courtNumber = courtNumber;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.evidenceFileKey = evidenceFileKey;
		this.evidenceReviewStatus = evidenceFileKey == null
				? EvidenceReviewStatus.NOT_SUBMITTED : EvidenceReviewStatus.PENDING;
	}

	public boolean isEvidenceVerified() {
		return evidenceReviewStatus == EvidenceReviewStatus.APPROVED;
	}

	public void approveEvidence(Instant now) {
		requirePendingEvidence();
		this.evidenceReviewStatus = EvidenceReviewStatus.APPROVED;
		this.reviewedAt = now;
	}

	public void rejectEvidence(String reason, Instant now) {
		requirePendingEvidence();
		this.evidenceReviewStatus = EvidenceReviewStatus.REJECTED;
		this.rejectionReason = reason;
		this.reviewedAt = now;
	}

	private void requirePendingEvidence() {
		if (evidenceReviewStatus != EvidenceReviewStatus.PENDING) {
			throw new GeneralException(CourtErrorStatus.EVIDENCE_REVIEW_NOT_PENDING);
		}
	}
}
