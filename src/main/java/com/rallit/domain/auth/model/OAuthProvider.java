package com.rallit.domain.auth.model;

import java.util.Arrays;

public enum OAuthProvider {
  KAKAO,
  GOOGLE;

  public String registrationId() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  public static OAuthProvider fromRegistrationId(String id) {
    return Arrays.stream(values())
        .filter(p -> p.registrationId().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported OAuth provider"));
  }
}
