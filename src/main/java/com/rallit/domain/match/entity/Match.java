package com.rallit.domain.match.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.global.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "match")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "court_slot_id", nullable = false, unique = true)
	private CourtSlot courtSlot;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "host_user_id", nullable = false)
	private User host;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MatchType matchType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MatchStatus status;

	@Column(nullable = false)
	private Integer maleRecruitCount;

	@Column(nullable = false)
	private Integer femaleRecruitCount;

	@Column(precision = 2, scale = 1)
	private BigDecimal minNtrp;

	@Column(precision = 2, scale = 1)
	private BigDecimal maxNtrp;

	@Column(nullable = false)
	private Integer participationFee;

	@Column(nullable = false)
	private LocalDateTime recruitmentDeadline;

	private LocalDateTime confirmedAt;

	private LocalDateTime completedAt;

	private LocalDateTime canceledAt;

	@Column(length = 500)
	private String cancelReason;

	@ElementCollection
	@CollectionTable(name = "match_preferred_age_group", joinColumns = @JoinColumn(name = "match_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "age_group", nullable = false, length = 30)
	private Set<AgeGroup> preferredAgeGroups = new HashSet<>();

	@Builder
	private Match(CourtSlot courtSlot, User host, MatchType matchType, Integer maleRecruitCount,
			Integer femaleRecruitCount, BigDecimal minNtrp, BigDecimal maxNtrp, Integer participationFee,
			LocalDateTime recruitmentDeadline, Set<AgeGroup> preferredAgeGroups) {
		this.courtSlot = courtSlot;
		this.host = host;
		this.matchType = matchType;
		this.status = MatchStatus.PENDING_APPROVAL;
		this.maleRecruitCount = maleRecruitCount;
		this.femaleRecruitCount = femaleRecruitCount;
		this.minNtrp = minNtrp;
		this.maxNtrp = maxNtrp;
		this.participationFee = participationFee;
		this.recruitmentDeadline = recruitmentDeadline;
		if (preferredAgeGroups != null) {
			this.preferredAgeGroups.addAll(preferredAgeGroups);
		}
	}
}
