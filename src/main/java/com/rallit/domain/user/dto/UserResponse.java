package com.rallit.domain.user.dto;

import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.UserRole;

public record UserResponse(
    Long id, String nickname, UserRole role, boolean onboardingCompleted, UserProfileResponse profile) {
  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(), user.getNickname(), user.getRole(), user.isOnboardingCompleted(),
        user.isOnboardingCompleted() ? UserProfileResponse.from(user) : null);
  }
}
