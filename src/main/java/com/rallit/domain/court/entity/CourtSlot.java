package com.rallit.domain.court.entity;

import java.time.LocalDateTime;

import com.rallit.domain.court.enums.CourtSlotStatus;
import com.rallit.domain.user.entity.User;
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
	private LocalDateTime startsAt;

	@Column(nullable = false)
	private LocalDateTime endsAt;

	@Column(nullable = false)
	private Integer rentalFee;

	@Column(nullable = false, length = 500)
	private String evidenceFileKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CourtSlotStatus status;

	@Column(length = 500)
	private String rejectionReason;

	private LocalDateTime reviewedAt;

	@Builder
	private CourtSlot(Court court, User registrant, String courtNumber, LocalDateTime startsAt, LocalDateTime endsAt,
			Integer rentalFee, String evidenceFileKey) {
		this.court = court;
		this.registrant = registrant;
		this.courtNumber = courtNumber;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.rentalFee = rentalFee;
		this.evidenceFileKey = evidenceFileKey;
		this.status = CourtSlotStatus.PENDING;
	}
}
