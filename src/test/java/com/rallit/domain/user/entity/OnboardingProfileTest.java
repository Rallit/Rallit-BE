package com.rallit.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.enums.PreferredTimeSlot;
import com.rallit.domain.user.exception.OnboardingProfileValidationException;
import com.rallit.domain.user.exception.OnboardingProfileValidationException.Reason;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OnboardingProfileTest {
  @Test
  void reportsDomainReasonsForInvalidProfiles() {
    Set<Area> areas = Set.of(mock(Area.class));
    assertThatThrownBy(() -> profile(null, areas, PreferredMatchType.SINGLES))
        .isInstanceOfSatisfying(OnboardingProfileValidationException.class,
            failure -> assertThat(failure.reason()).isEqualTo(Reason.INVALID_REQUIRED_VALUES));
    assertThatThrownBy(() -> profile("테니스친구", Set.of(), PreferredMatchType.SINGLES))
        .isInstanceOfSatisfying(OnboardingProfileValidationException.class,
            failure -> assertThat(failure.reason()).isEqualTo(Reason.INVALID_ACTIVITY_AREA));
    assertThatThrownBy(() -> profile("테니스친구", areas, PreferredMatchType.MIXED_DOUBLES))
        .isInstanceOfSatisfying(OnboardingProfileValidationException.class,
            failure -> assertThat(failure.reason()).isEqualTo(Reason.INVALID_MATCH_PREFERENCES));
    assertThatThrownBy(() -> profile("테니스친구", areas, PreferredMatchType.SINGLES, null))
        .isInstanceOfSatisfying(OnboardingProfileValidationException.class,
            failure -> assertThat(failure.reason()).isEqualTo(Reason.INVALID_REQUIRED_VALUES));
  }

  @Test
  void allowsTennisStartFromEarliestMonthUntilCurrentMonth() {
    Set<Area> areas = Set.of(mock(Area.class));
    YearMonth currentMonth = YearMonth.of(2026, 10);
    assertThatNoException().isThrownBy(() -> profile("테니스친구", areas, PreferredMatchType.SINGLES,
        OnboardingProfile.EARLIEST_TENNIS_START).requireTennisStartedBy(currentMonth));
    assertThatNoException().isThrownBy(() -> profile("테니스친구", areas, PreferredMatchType.SINGLES,
        currentMonth).requireTennisStartedBy(currentMonth));
    for (YearMonth outOfRange : Set.of(OnboardingProfile.EARLIEST_TENNIS_START.minusMonths(1),
        currentMonth.plusMonths(1))) {
      assertThatThrownBy(() -> profile("테니스친구", areas, PreferredMatchType.SINGLES, outOfRange)
          .requireTennisStartedBy(currentMonth))
          .isInstanceOfSatisfying(OnboardingProfileValidationException.class,
              failure -> assertThat(failure.reason()).isEqualTo(Reason.INVALID_TENNIS_START));
    }
  }

  private OnboardingProfile profile(String nickname, Set<Area> areas, PreferredMatchType matchType) {
    return profile(nickname, areas, matchType, YearMonth.of(2024, 11));
  }

  private OnboardingProfile profile(String nickname, Set<Area> areas, PreferredMatchType matchType,
      YearMonth tennisStartedOn) {
    return new OnboardingProfile(nickname, Gender.FEMALE, AgeGroup.TWENTIES,
        tennisStartedOn, BigDecimal.valueOf(3), true,
        Set.of(matchType), areas, Set.of(PreferredTimeSlot.WEEKEND_MORNING));
  }
}
