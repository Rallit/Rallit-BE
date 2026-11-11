package com.rallit.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rallit.domain.notification.entity.Notification;
import com.rallit.domain.notification.enums.NotificationType;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.support.EntityFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private EntityManager entityManager;

	private User recipient;

	@BeforeEach
	void setUp() {
		recipient = userRepository.save(EntityFixtures.user("알림수신자", Gender.FEMALE));
	}

	@Test
	void savesUnreadNotification() {
		Notification notification = notificationRepository.saveAndFlush(notification("court-approved:1"));
		entityManager.clear();

		Notification found = notificationRepository.findById(notification.getId()).orElseThrow();

		assertThat(found.getType()).isEqualTo(NotificationType.COURT_APPROVED);
		assertThat(found.getReadAt()).isNull();
		assertThat(found.getMatch()).isNull();
	}

	@Test
	void rejectsDuplicateDedupeKey() {
		notificationRepository.saveAndFlush(notification("court-approved:1"));

		assertThatThrownBy(() -> notificationRepository.saveAndFlush(notification("court-approved:1")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_notification_dedupe_key");
	}

	private Notification notification(String dedupeKey) {
		return Notification.builder()
				.recipient(recipient)
				.type(NotificationType.COURT_APPROVED)
				.title("코트 인증이 승인됐어요")
				.body("매치 모집이 시작됐어요.")
				.dedupeKey(dedupeKey)
				.build();
	}
}
