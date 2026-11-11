package com.rallit.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rallit.auth.google")
public record GoogleOAuthProperties(
    String clientId,
    String clientSecret,
    String redirectUri,
    String authorizationUri,
    String tokenUri,
    String jwkSetUri,
    String userInfoUri) {
  public GoogleOAuthProperties {
    if (blank(clientId) || blank(clientSecret) || blank(redirectUri))
      throw new IllegalArgumentException(
          "Google OAuth client ID, client secret and redirect URI are required");
  }

  @Override
  public String toString() {
    return "GoogleOAuthProperties[redacted]";
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
