package com.rallit.domain.user.dto;

import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
    Long id,
    @Schema(description = "온보딩 입력칸에 채울 기본 닉네임 또는 가입 완료 후 저장된 닉네임")
    String nickname,
    UserRole role,
    boolean onboardingCompleted,
    @Schema(description = "백엔드 기준 상대 경로. 조회에 Bearer 토큰이 필요하며, 올린 사진이 없으면 null",
        example = "/api/v1/users/1/profile-image", types = {"string", "null"})
    String profileImageUrl,
    @Schema(description = "카카오·구글 소셜 로그인에서 받은 표시 이름", types = {"string", "null"})
    String socialDisplayName,
    @Schema(description = "저장된 온보딩 프로필. 가입 완료 전에는 null")
    UserProfileResponse profile) {
  public static UserResponse from(User user, String socialDisplayName) {
    return new UserResponse(
        user.getId(), user.getNickname(), user.getRole(), user.isOnboardingCompleted(),
        user.getProfileImageKey() == null ? null : ProfileImageResponse.urlFor(user.getId()),
        socialDisplayName,
        user.isOnboardingCompleted() ? UserProfileResponse.from(user) : null);
  }
}
