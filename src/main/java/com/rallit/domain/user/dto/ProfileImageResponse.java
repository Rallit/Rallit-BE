package com.rallit.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileImageResponse(
    @Schema(description = ProfileImageResponse.URL_DESCRIPTION, example = "/api/v1/users/1/profile-image")
    String profileImageUrl) {
  public static final String URL_DESCRIPTION =
      "이미지 상대 경로. 끝에 /url을 붙여 Bearer 인증으로 요청하면 Presigned GET URL을 발급합니다.";

  public static String urlFor(Long userId) {
    return "/api/v1/users/" + userId + "/profile-image";
  }
}
