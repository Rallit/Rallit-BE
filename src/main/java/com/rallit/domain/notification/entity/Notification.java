package com.rallit.domain.notification.entity;

import java.time.LocalDateTime;

import com.rallit.domain.match.entity.Match;
import com.rallit.domain.notification.enums.NotificationType;
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
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipient_user_id", nullable = false)
	private User recipient;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "match_id")
	private Match match;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 500)
	private String body;

	@Column(unique = true, length = 200)
	private String dedupeKey;

	private LocalDateTime readAt;

	@Builder
	private Notification(User recipient, Match match, NotificationType type, String title, String body,
			String dedupeKey) {
		this.recipient = recipient;
		this.match = match;
		this.type = type;
		this.title = title;
		this.body = body;
		this.dedupeKey = dedupeKey;
	}
}
