package com.rallit.domain.settlement.entity;

import java.time.LocalDateTime;

import com.rallit.domain.match.entity.Match;
import com.rallit.domain.settlement.enums.SettlementStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "match_id", nullable = false, unique = true)
	private Match match;

	@Column(nullable = false)
	private Integer amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SettlementStatus status;

	private LocalDateTime settledAt;

	private Settlement(Match match, Integer amount) {
		this.match = match;
		this.amount = amount;
		this.status = SettlementStatus.PENDING;
	}

	public static Settlement create(Match match, Integer amount) {
		return new Settlement(match, amount);
	}
}
