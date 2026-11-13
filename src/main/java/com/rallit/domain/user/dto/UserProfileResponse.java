package com.rallit.domain.user.dto;

import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.*;
import java.math.BigDecimal;
import java.util.List;

public record UserProfileResponse(
    Gender gender,
    AgeGroup ageGroup,
    ExperiencePeriod experiencePeriod,
    String ntrp,
    boolean sameGenderOnly,
    List<PreferredMatchType> preferredMatchTypes,
    List<String> activityAreaCodes,
    List<PreferredTimeSlot> preferredTimeSlots) {

  public static UserProfileResponse from(User user) {
    return new UserProfileResponse(user.getGender(), user.getAgeGroup(), user.getExperiencePeriod(),
        formatNtrp(user.getNtrp()), user.isSameGenderOnly(),
        user.getPreferredMatchTypes().stream().sorted().toList(),
        user.getActivityAreas().stream().map(Area::getCode).sorted().toList(),
        user.getPreferredTimeSlots().stream().sorted().toList());
  }

  private static String formatNtrp(BigDecimal ntrp) {
    if (ntrp == null) return null;
    return ntrp.compareTo(BigDecimal.valueOf(4)) >= 0 ? "4.0+" : ntrp.toPlainString();
  }
}
