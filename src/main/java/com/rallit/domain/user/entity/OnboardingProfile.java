package com.rallit.domain.user.entity;

import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.enums.*;
import com.rallit.domain.user.exception.UserErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

public record OnboardingProfile(
    String nickname,
    Gender gender,
    AgeGroup ageGroup,
    ExperiencePeriod experiencePeriod,
    BigDecimal ntrp,
    boolean sameGenderOnly,
    Set<PreferredMatchType> preferredMatchTypes,
    Set<Area> activityAreas,
    Set<PreferredTimeSlot> preferredTimeSlots) {

  public static final String NICKNAME_PATTERN = "[가-힣a-zA-Z0-9]{2,12}";

  public OnboardingProfile {
    Objects.requireNonNull(nickname);
    Objects.requireNonNull(gender);
    Objects.requireNonNull(ageGroup);
    Objects.requireNonNull(experiencePeriod);
    Objects.requireNonNull(ntrp);
    preferredMatchTypes = Set.copyOf(preferredMatchTypes);
    activityAreas = Set.copyOf(activityAreas);
    preferredTimeSlots = Set.copyOf(preferredTimeSlots);
    if (!nickname.matches(NICKNAME_PATTERN)
        || isInvalidNtrp(ntrp)
        || preferredMatchTypes.isEmpty() || preferredTimeSlots.isEmpty()) {
      throw new IllegalArgumentException("Invalid required onboarding profile values");
    }
    if (activityAreas.isEmpty() || activityAreas.size() > 3) {
      throw new GeneralException(UserErrorStatus.INVALID_ACTIVITY_AREA);
    }
    if (sameGenderOnly && preferredMatchTypes.contains(PreferredMatchType.MIXED_DOUBLES)) {
      throw new GeneralException(UserErrorStatus.INVALID_MATCH_PREFERENCES);
    }
  }

  private static boolean isInvalidNtrp(BigDecimal ntrp) {
    return ntrp.compareTo(BigDecimal.ONE) < 0
        || ntrp.compareTo(BigDecimal.valueOf(4)) > 0
        || ntrp.remainder(new BigDecimal("0.5")).signum() != 0;
  }
}
