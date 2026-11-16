package com.rallit.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.enums.PreferredTimeSlot;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserTest {
  @ParameterizedTest
  @CsvSource({
      "2024-11, 2026-10, 1",
      "2024-10, 2026-10, 2",
      "2024-12, 2025-11, 0",
      "2025-11, 2026-11, 1",
      "2026-10, 2026-10, 0"
  })
  void countsCompletedYearsSinceTennisStartMonth(YearMonth startedOn, YearMonth currentMonth, int expected) {
    User user = User.signUp();
    user.completeOnboarding(new OnboardingProfile("테니스친구", Gender.FEMALE, AgeGroup.TWENTIES, startedOn,
        BigDecimal.valueOf(3), false, Set.of(PreferredMatchType.SINGLES), Set.of(mock(Area.class)),
        Set.of(PreferredTimeSlot.WEEKEND_MORNING)));

    assertThat(user.getTennisStartedOn()).isEqualTo(startedOn);
    assertThat(user.experienceYears(currentMonth)).isEqualTo(expected);
  }

  @Test
  void hasNoExperienceYearsWithoutTennisStartMonth() {
    assertThat(User.signUp().experienceYears(YearMonth.of(2026, 10))).isNull();
  }
}
