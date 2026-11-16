package com.rallit.domain.match.entity;

import java.time.LocalDateTime;

import com.rallit.domain.user.entity.User;
import com.rallit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "match_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRequest extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private User owner;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private MatchRequestConditions conditions;

	@Column(nullable = false, updatable = false)
	private LocalDateTime expiresAt;

	private LocalDateTime deletedAt;

	public MatchRequest(User owner, MatchRequestConditions conditions, LocalDateTime expiresAt) {
		this.owner = owner;
		this.conditions = conditions;
		this.expiresAt = expiresAt;
	}

	public void delete(LocalDateTime now) {
		this.deletedAt = now;
	}
}
