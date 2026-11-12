package com.rallit.domain.user.dto;

import static com.rallit.domain.user.entity.OnboardingProfile.NICKNAME_PATTERN;

import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.entity.OnboardingProfile;
import com.rallit.domain.user.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Set;

public record OnboardingRequest(
    @NotNull @Pattern(regexp = NICKNAME_PATTERN) String nickname,
    @NotNull Gender gender,
    @NotNull AgeGroup ageGroup,
    @NotNull ExperiencePeriod experiencePeriod,
    @NotNull @Pattern(regexp = "(?:[1-3]\\.[05]|4\\.0\\+)")
        @Schema(allowableValues = {"1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0+"}) String ntrp,
    @NotNull Boolean sameGenderOnly,
    @NotEmpty Set<@NotNull PreferredMatchType> preferredMatchTypes,
    @NotNull @Size(min = 1, max = 3)
        Set<@NotNull @Pattern(regexp = "[0-9]{5}") String> activityAreaCodes,
    @NotEmpty Set<@NotNull PreferredTimeSlot> preferredTimeSlots) {

  public OnboardingProfile toProfile(Set<Area> areas) {
    return new OnboardingProfile(nickname, gender, ageGroup, experiencePeriod,
        new BigDecimal(ntrp.replace("+", "")), sameGenderOnly,
        preferredMatchTypes, areas, preferredTimeSlots);
  }
}
