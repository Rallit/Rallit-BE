package com.rallit.domain.user.entity;

import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.enums.*;
import com.rallit.domain.user.exception.OnboardingProfileValidationException;
import com.rallit.domain.user.exception.OnboardingProfileValidationException.Reason;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;

public record OnboardingProfile(
    String nickname,
    Gender gender,
    AgeGroup ageGroup,
    YearMonth tennisStartedOn,
    BigDecimal ntrp,
    boolean sameGenderOnly,
    Set<PreferredMatchType> preferredMatchTypes,
    Set<Area> activityAreas,
    Set<PreferredTimeSlot> preferredTimeSlots) {

  public static final String NICKNAME_PATTERN = "[가-힣a-zA-Z0-9]{2,12}";
  public static final YearMonth EARLIEST_TENNIS_START = YearMonth.of(1900, 1);

  public OnboardingProfile {
    if (nickname == null || !nickname.matches(NICKNAME_PATTERN)
        || gender == null || ageGroup == null || tennisStartedOn == null
        || !Ntrp.isSupported(ntrp)
        || invalidSelection(preferredMatchTypes) || invalidSelection(preferredTimeSlots)) {
      throw new OnboardingProfileValidationException(Reason.INVALID_REQUIRED_VALUES);
    }
    if (invalidSelection(activityAreas) || activityAreas.size() > 3) {
      throw new OnboardingProfileValidationException(Reason.INVALID_ACTIVITY_AREA);
    }
    if (sameGenderOnly && preferredMatchTypes.contains(PreferredMatchType.MIXED_DOUBLES)) {
      throw new OnboardingProfileValidationException(Reason.INVALID_MATCH_PREFERENCES);
    }
    preferredMatchTypes = Set.copyOf(preferredMatchTypes);
    activityAreas = Set.copyOf(activityAreas);
    preferredTimeSlots = Set.copyOf(preferredTimeSlots);
  }

  public void requireTennisStartedBy(YearMonth currentMonth) {
    if (tennisStartedOn.isBefore(EARLIEST_TENNIS_START) || tennisStartedOn.isAfter(currentMonth)) {
      throw new OnboardingProfileValidationException(Reason.INVALID_TENNIS_START);
    }
  }

  private static boolean invalidSelection(Set<?> values) {
    return values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull);
  }
}
