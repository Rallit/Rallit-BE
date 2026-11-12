package com.rallit.domain.user.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.ExperiencePeriod;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.enums.PreferredTimeSlot;
import com.rallit.domain.user.enums.UserRole;
import com.rallit.domain.user.enums.UserStatus;
import com.rallit.global.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserStatus status;

	@Column(unique = true, length = 20)
	private String nickname;

	@Column(length = 320)
	private String email;

	@Column(length = 500)
	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private Gender gender;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private AgeGroup ageGroup;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private ExperiencePeriod experiencePeriod;

	@Column(precision = 2, scale = 1)
	private BigDecimal ntrp;

	@Column(nullable = false)
	private boolean sameGenderOnly;

	private LocalDateTime withdrawnAt;

	@ElementCollection
	@CollectionTable(name = "user_preferred_match_type", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "match_type", nullable = false, length = 30)
	private Set<PreferredMatchType> preferredMatchTypes = new HashSet<>();

	@ManyToMany
	@JoinTable(name = "user_activity_areas",
			joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "area_id"))
	private Set<Area> activityAreas = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "user_preferred_time_slot", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "time_slot", nullable = false, length = 30)
	private Set<PreferredTimeSlot> preferredTimeSlots = new HashSet<>();

	private User(String profileImageUrl) {
		this.role = UserRole.USER;
		this.status = UserStatus.ONBOARDING;
		this.profileImageUrl = profileImageUrl;
	}

	public static User signUp(String profileImageUrl) {
		return new User(profileImageUrl);
	}

	public static User signUp(String profileImageUrl, String nickname, String verifiedEmail) {
		User user = signUp(profileImageUrl);
		user.nickname = nickname;
		user.updateVerifiedEmail(verifiedEmail);
		return user;
	}

	public void updateVerifiedEmail(String email) {
		this.email = Objects.requireNonNull(email);
	}

	public boolean isWithdrawn() {
		return status == UserStatus.WITHDRAWN;
	}

	public boolean isOnboardingCompleted() {
		return status == UserStatus.ACTIVE;
	}

	public void completeOnboarding(OnboardingProfile profile) {
		if (status != UserStatus.ONBOARDING) {
			throw new IllegalStateException("User is not awaiting onboarding");
		}
		nickname = profile.nickname();
		gender = profile.gender();
		ageGroup = profile.ageGroup();
		experiencePeriod = profile.experiencePeriod();
		ntrp = profile.ntrp();
		sameGenderOnly = profile.sameGenderOnly();
		preferredMatchTypes.clear();
		preferredMatchTypes.addAll(profile.preferredMatchTypes());
		activityAreas.clear();
		activityAreas.addAll(profile.activityAreas());
		preferredTimeSlots.clear();
		preferredTimeSlots.addAll(profile.preferredTimeSlots());
		status = UserStatus.ACTIVE;
	}

	public boolean hasOnboardingProfile(OnboardingProfile profile) {
		return Objects.equals(nickname, profile.nickname())
				&& gender == profile.gender()
				&& ageGroup == profile.ageGroup()
				&& experiencePeriod == profile.experiencePeriod()
				&& ntrp != null && ntrp.compareTo(profile.ntrp()) == 0
				&& sameGenderOnly == profile.sameGenderOnly()
				&& preferredMatchTypes.equals(profile.preferredMatchTypes())
				&& areaCodes(activityAreas).equals(areaCodes(profile.activityAreas()))
				&& preferredTimeSlots.equals(profile.preferredTimeSlots());
	}

	private Set<String> areaCodes(Set<Area> areas) {
		return areas.stream().map(Area::getCode).collect(Collectors.toSet());
	}
}
