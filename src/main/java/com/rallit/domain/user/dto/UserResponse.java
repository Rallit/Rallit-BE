package com.rallit.domain.user.dto;

import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;

public record UserResponse(
    Long id,
    @Schema(description = "온보딩 입력칸에 채울 기본 닉네임 또는 가입 완료 후 저장된 닉네임")
    String nickname,
    UserRole role,
    boolean onboardingCompleted,
    @Schema(description = ProfileImageResponse.URL_DESCRIPTION + " 등록된 사진이 없으면 null입니다.",
        example = "/api/v1/users/1/profile-image", types = {"string", "null"})
    String profileImageUrl,
    @Schema(description = "카카오·구글 소셜 로그인에서 받은 온보딩 환영 인사용 소셜 이름. 값이 없으면 null.",
        types = {"string", "null"})
    String socialDisplayName,
    @Schema(description = "저장된 온보딩 프로필. 가입 완료 전에는 null")
    UserProfileResponse profile) {
  public static UserResponse from(User user, String socialDisplayName, YearMonth currentMonth) {
    return new UserResponse(
        user.getId(), user.getNickname(), user.getRole(), user.isOnboardingCompleted(),
        user.getProfileImageKey() == null ? null : ProfileImageResponse.urlFor(user.getId()),
        socialDisplayName,
        user.isOnboardingCompleted() ? UserProfileResponse.from(user, currentMonth) : null);
  }
}
