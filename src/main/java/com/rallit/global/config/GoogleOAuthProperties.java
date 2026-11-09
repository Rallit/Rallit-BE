package com.rallit.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rallit.auth.google")
public record GoogleOAuthProperties(
    boolean enabled,
    String clientId,
    String clientSecret,
    String redirectUri,
    String authorizationUri,
    String tokenUri,
    String jwkSetUri,
    String userInfoUri) {
  public GoogleOAuthProperties {
    if (enabled && (blank(clientId) || blank(clientSecret) || blank(redirectUri)))
      throw new IllegalArgumentException(
          "Google OAuth credentials and redirect URI required when enabled");
  }

  @Override
  public String toString() {
    return "GoogleOAuthProperties[enabled=" + enabled + "]";
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
