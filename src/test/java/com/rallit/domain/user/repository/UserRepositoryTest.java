package com.rallit.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;

import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.entity.SocialAccount;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.ExperiencePeriod;
import com.rallit.domain.user.enums.Gender;
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
		user.onboarding()
				.nickname("테니스곰123")
				.gender(Gender.FEMALE)
				.ageGroup(AgeGroup.TWENTIES)
				.experiencePeriod(ExperiencePeriod.SIX_YEARS_OR_MORE)
				.ntrp(new BigDecimal("3.0"))
				.tournamentHistory("2025 대학 연맹전 3위")
				.sameGenderOnly(true)
				.activityLatitude(new BigDecimal("37.556600"))
				.activityLongitude(new BigDecimal("126.923600"))
				.activityRadiusKm(10)
				.preferredMatchTypes(Set.of(MatchType.SINGLES, MatchType.WOMENS_DOUBLES))
				.preferredTimeSlots(Set.of(PreferredTimeSlot.WEEKEND_MORNING))
				.complete();
		userRepository.saveAndFlush(user);
		entityManager.clear();

		User found = userRepository.findById(user.getId()).orElseThrow();

		assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(found.getNickname()).isEqualTo("테니스곰123");
		assertThat(found.getNtrp()).isEqualByComparingTo("3.0");
		assertThat(found.getExperiencePeriod()).isEqualTo(ExperiencePeriod.SIX_YEARS_OR_MORE);
		assertThat(found.isSameGenderOnly()).isTrue();
		assertThat(found.getActivityRadiusKm()).isEqualTo(10);
		assertThat(found.getPreferredMatchTypes())
				.containsExactlyInAnyOrder(MatchType.SINGLES, MatchType.WOMENS_DOUBLES);
		assertThat(found.getPreferredTimeSlots()).containsExactly(PreferredTimeSlot.WEEKEND_MORNING);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(jdbcTemplate.queryForObject("SELECT experience_period FROM users WHERE id = ?", String.class,
				user.getId())).isEqualTo("SIX_YEARS_OR_MORE");
	}

	@Test
	void rejectsDuplicateNickname() {
		userRepository.saveAndFlush(EntityFixtures.user("랠리왕", Gender.MALE));

		assertThatThrownBy(() -> userRepository.saveAndFlush(EntityFixtures.user("랠리왕", Gender.FEMALE)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_users_nickname");
	}

	@Test
	void rejectsActiveUserWithoutGender() {
		User user = User.signUp(null);
		user.onboarding()
				.nickname("성별누락")
				.complete();

		assertThatThrownBy(() -> userRepository.saveAndFlush(user))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_users_active_profile");
	}

	@Test
	void rejectsActivityLatitudeWithoutLongitude() {
		User user = User.signUp(null);
		user.onboarding()
				.nickname("위치누락")
				.gender(Gender.MALE)
				.activityLatitude(new BigDecimal("37.556600"))
				.complete();

		assertThatThrownBy(() -> userRepository.saveAndFlush(user))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_users_activity_location");
	}

	@Test
	void savesSocialAccountsOfDifferentProvidersForOneUser() {
		User user = userRepository.save(EntityFixtures.user("소셜회원", Gender.MALE));
		socialAccountRepository.saveAndFlush(socialAccount(user, SocialProvider.KAKAO, "1234567890"));
		socialAccountRepository.saveAndFlush(socialAccount(user, SocialProvider.GOOGLE, "1234567890"));

		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from social_account where user_id = ?", Long.class, user.getId()))
				.isEqualTo(2L);
	}

	@Test
	void rejectsDuplicateProviderUserId() {
		User first = userRepository.save(EntityFixtures.user("첫번째", Gender.MALE));
		User second = userRepository.save(EntityFixtures.user("두번째", Gender.FEMALE));
		socialAccountRepository.saveAndFlush(socialAccount(first, SocialProvider.KAKAO, "1234567890"));

		assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(
				socialAccount(second, SocialProvider.KAKAO, "1234567890")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_social_account_provider_provider_user_id");
	}

	@Test
	void rejectsSecondAccountOfSameProviderForOneUser() {
		User user = userRepository.save(EntityFixtures.user("카카오두개", Gender.MALE));
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
