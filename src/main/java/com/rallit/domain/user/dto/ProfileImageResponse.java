package com.rallit.domain.user.dto;

public record ProfileImageResponse(String profileImageUrl) {
  public static String urlFor(Long userId) {
    return "/api/v1/users/" + userId + "/profile-image";
  }
}
