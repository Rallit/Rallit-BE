package com.rallit.domain.user.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.ExperiencePeriod;
import com.rallit.domain.user.enums.Gender;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
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

	@Column(length = 500)
	private String tournamentHistory;

	@Column(nullable = false)
	private boolean sameGenderOnly;

	@Column(precision = 9, scale = 6)
	private BigDecimal activityLatitude;

	@Column(precision = 9, scale = 6)
	private BigDecimal activityLongitude;

	private Integer activityRadiusKm;

	private LocalDateTime withdrawnAt;

	@ElementCollection
	@CollectionTable(name = "user_preferred_match_type", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "match_type", nullable = false, length = 30)
	private Set<MatchType> preferredMatchTypes = new HashSet<>();

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

	public boolean isOnboardingCompleted() {
		return status == UserStatus.ACTIVE;
	}

	@Builder(builderMethodName = "onboarding", buildMethodName = "complete", builderClassName = "OnboardingBuilder")
	private void completeOnboarding(String nickname, Gender gender, AgeGroup ageGroup,
			ExperiencePeriod experiencePeriod, BigDecimal ntrp, String tournamentHistory, boolean sameGenderOnly,
			BigDecimal activityLatitude, BigDecimal activityLongitude, Integer activityRadiusKm,
			Set<MatchType> preferredMatchTypes, Set<PreferredTimeSlot> preferredTimeSlots) {
		this.status = UserStatus.ACTIVE;
		this.nickname = nickname;
		this.gender = gender;
		this.ageGroup = ageGroup;
		this.experiencePeriod = experiencePeriod;
		this.ntrp = ntrp;
		this.tournamentHistory = tournamentHistory;
		this.sameGenderOnly = sameGenderOnly;
		this.activityLatitude = activityLatitude;
		this.activityLongitude = activityLongitude;
		this.activityRadiusKm = activityRadiusKm;
		this.preferredMatchTypes.clear();
		if (preferredMatchTypes != null) {
			this.preferredMatchTypes.addAll(preferredMatchTypes);
		}
		this.preferredTimeSlots.clear();
		if (preferredTimeSlots != null) {
			this.preferredTimeSlots.addAll(preferredTimeSlots);
		}
	}
}
