package com.rallit.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;

import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.entity.OnboardingProfile;
import com.rallit.domain.user.entity.SocialAccount;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.ExperiencePeriod;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.enums.PreferredTimeSlot;
import com.rallit.domain.user.enums.SocialProvider;
import com.rallit.domain.user.enums.UserRole;
import com.rallit.domain.user.enums.UserStatus;
import com.rallit.support.EntityFixtures;
import jakarta.persistence.EntityManager;
import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest extends IntegrationTestSupport {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void savesOnboardingUserRightAfterSocialLogin() {
		User first = userRepository.saveAndFlush(User.signUp("https://k.kakaocdn.net/profile.png"));
		User second = userRepository.saveAndFlush(User.signUp(null));
		entityManager.clear();

		User found = userRepository.findById(first.getId()).orElseThrow();

		assertThat(found.getRole()).isEqualTo(UserRole.USER);
		assertThat(found.getStatus()).isEqualTo(UserStatus.ONBOARDING);
		assertThat(found.getProfileImageUrl()).isEqualTo("https://k.kakaocdn.net/profile.png");
		assertThat(found.getNickname()).isNull();
		assertThat(userRepository.findById(second.getId()).orElseThrow().getNickname()).isNull();
	}

	@Test
	void completesOnboardingWithTennisProfileAndPreferences() {
		User user = User.signUp(null);
		Area area = entityManager.createQuery("select a from Area a where a.code = '11440'", Area.class)
				.getSingleResult();
		user.completeOnboarding(new OnboardingProfile("테니스곰123", Gender.FEMALE, AgeGroup.TWENTIES,
				ExperiencePeriod.THREE_YEARS_OR_MORE, new BigDecimal("3.0"), true,
				Set.of(PreferredMatchType.SINGLES, PreferredMatchType.DOUBLES), Set.of(area),
				Set.of(PreferredTimeSlot.WEEKEND_MORNING)));
		userRepository.saveAndFlush(user);
		entityManager.clear();

		User found = userRepository.findById(user.getId()).orElseThrow();

		assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(found.getNickname()).isEqualTo("테니스곰123");
		assertThat(found.getNtrp()).isEqualByComparingTo("3.0");
		assertThat(found.getExperiencePeriod()).isEqualTo(ExperiencePeriod.THREE_YEARS_OR_MORE);
		assertThat(found.isSameGenderOnly()).isTrue();
		assertThat(found.getActivityAreas()).extracting(Area::getCode).containsExactly("11440");
		assertThat(found.getPreferredMatchTypes())
				.containsExactlyInAnyOrder(PreferredMatchType.SINGLES, PreferredMatchType.DOUBLES);
		assertThat(found.getPreferredTimeSlots()).containsExactly(PreferredTimeSlot.WEEKEND_MORNING);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(jdbcTemplate.queryForObject("SELECT experience_period FROM users WHERE id = ?", String.class,
				user.getId())).isEqualTo("THREE_YEARS_OR_MORE");
	}

	@Test
	void rejectsDuplicateNickname() {
		userRepository.saveAndFlush(EntityFixtures.user(entityManager, "랠리왕", Gender.MALE));

		assertThatThrownBy(() -> userRepository.saveAndFlush(EntityFixtures.user(entityManager, "랠리왕", Gender.FEMALE)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_users_nickname");
	}

	@Test
	void rejectsActiveUserWithoutGender() {
		User user = userRepository.saveAndFlush(User.signUp(null));
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE users SET status = 'ACTIVE', nickname = '성별누락' WHERE id = ?", user.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_users_active_profile");
	}

	@Test
	void savesSocialAccountsOfDifferentProvidersForOneUser() {
		User user = userRepository.save(EntityFixtures.user(entityManager, "소셜회원", Gender.MALE));
		socialAccountRepository.saveAndFlush(socialAccount(user, SocialProvider.KAKAO, "1234567890"));
		socialAccountRepository.saveAndFlush(socialAccount(user, SocialProvider.GOOGLE, "1234567890"));

		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from social_account where user_id = ?", Long.class, user.getId()))
				.isEqualTo(2L);
	}

	@Test
	void rejectsDuplicateProviderUserId() {
		User first = userRepository.save(EntityFixtures.user(entityManager, "첫번째", Gender.MALE));
		User second = userRepository.save(EntityFixtures.user(entityManager, "두번째", Gender.FEMALE));
		socialAccountRepository.saveAndFlush(socialAccount(first, SocialProvider.KAKAO, "1234567890"));

		assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(
				socialAccount(second, SocialProvider.KAKAO, "1234567890")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_social_account_provider_provider_user_id");
	}

	@Test
	void rejectsSecondAccountOfSameProviderForOneUser() {
		User user = userRepository.save(EntityFixtures.user(entityManager, "카카오두개", Gender.MALE));
		socialAccountRepository.saveAndFlush(socialAccount(user, SocialProvider.KAKAO, "1111111111"));

		assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(
				socialAccount(user, SocialProvider.KAKAO, "2222222222")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_social_account_user_id_provider");
	}

	private SocialAccount socialAccount(User user, SocialProvider provider, String providerUserId) {
		return SocialAccount.of(user, provider, providerUserId);
	}
}
