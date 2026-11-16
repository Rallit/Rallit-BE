package com.rallit.domain.user.dto;

import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record UserProfileResponse(
    Gender gender,
    AgeGroup ageGroup,
    @Schema(description = "테니스 시작 연월. 입력하지 않은 기존 회원은 null",
        types = {"string", "null"}, example = "2025-10") YearMonth tennisStartedOn,
    @Schema(description = "현재 연월 기준 만 구력(년). 1년 미만은 0, 시작 연월이 없으면 null",
        types = {"integer", "null"}, example = "1") Integer experienceYears,
    String ntrp,
    boolean sameGenderOnly,
    List<PreferredMatchType> preferredMatchTypes,
    List<String> activityAreaCodes,
    List<PreferredTimeSlot> preferredTimeSlots) {

  public static UserProfileResponse from(User user, YearMonth currentMonth) {
    return new UserProfileResponse(user.getGender(), user.getAgeGroup(), user.getTennisStartedOn(),
        user.experienceYears(currentMonth),
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
