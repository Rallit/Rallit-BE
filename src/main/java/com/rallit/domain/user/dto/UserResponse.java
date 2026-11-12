package com.rallit.domain.user.dto;

import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.UserRole;

public record UserResponse(
    Long id, String nickname, UserRole role, boolean onboardingCompleted,
    String profileImageUrl, String socialDisplayName, UserProfileResponse profile) {
  public static UserResponse from(User user, String socialDisplayName) {
    return new UserResponse(
        user.getId(), user.getNickname(), user.getRole(), user.isOnboardingCompleted(),
        user.getProfileImageKey() == null ? null : ProfileImageResponse.urlFor(user.getId()),
        socialDisplayName,
        user.isOnboardingCompleted() ? UserProfileResponse.from(user) : null);
  }
}
