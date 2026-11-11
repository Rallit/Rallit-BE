package com.rallit.domain.user.entity;

import com.rallit.domain.user.enums.SocialProvider;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "social_account", uniqueConstraints = {
	@UniqueConstraint(name = "uk_social_account_provider_provider_user_id", columnNames = {"provider", "provider_user_id"}),
	@UniqueConstraint(name = "uk_social_account_user_id_provider", columnNames = {"user_id", "provider"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SocialProvider provider;

	@Column(nullable = false)
	private String providerUserId;

	private SocialAccount(User user, SocialProvider provider, String providerUserId) {
		this.user = user;
		this.provider = provider;
		this.providerUserId = providerUserId;
	}

	public static SocialAccount of(User user, SocialProvider provider, String providerUserId) {
		return new SocialAccount(user, provider, providerUserId);
	}
}
