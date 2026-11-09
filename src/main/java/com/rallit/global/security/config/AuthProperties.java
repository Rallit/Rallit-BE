package com.rallit.global.security.config;

import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("rallit.auth")
@Validated
public record AuthProperties(
    @NotBlank String serviceKey,
    @NotBlank String adminKey,
    @NotBlank String frontendCallbackUrl,
    @NotBlank String adminFrontendCallbackUrl,
    @NotBlank String kakaoClientId,
    String kakaoClientSecret,
    @NotBlank String kakaoRedirectUri,
    String kakaoAuthorizationUri,
    String kakaoTokenUri,
    String kakaoUserInfoUri,
    Boolean secureCookies,
    List<String> serviceOrigins,
    List<String> adminOrigins) {
  public AuthProperties {
    validateCallback(frontendCallbackUrl, !Boolean.FALSE.equals(secureCookies), "rallit.co.kr");
    validateCallback(
        adminFrontendCallbackUrl, !Boolean.FALSE.equals(secureCookies), "admin.rallit.co.kr");
  }

  private static void validateCallback(String value, boolean secure, String host) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Callback URL required");
    java.net.URI u = java.net.URI.create(value);
    if (u.getHost() == null
        || u.getUserInfo() != null
        || u.getQuery() != null
        || u.getFragment() != null
        || !("https".equals(u.getScheme()) || (!secure && "http".equals(u.getScheme()))))
      throw new IllegalArgumentException("Invalid callback URL configuration");
    if (secure && (!host.equals(u.getHost()) || (u.getPort() != -1 && u.getPort() != 443)))
      throw new IllegalArgumentException("Invalid callback host configuration");
  }

  public String callbackUrl(com.rallit.domain.auth.model.LoginTarget target) {
    return switch (target) {
      case SERVICE -> frontendCallbackUrl;
      case ADMIN -> adminFrontendCallbackUrl;
    };
  }

  public String cookieName(com.rallit.domain.auth.model.LoginTarget t, String kind) {
    return (secure() ? "__Host-" : "") + t.prefix() + "_" + kind;
  }

  public String cookiePath(com.rallit.domain.auth.model.LoginTarget t, boolean refresh) {
    return secure() ? "/" : t.apiPath() + (refresh ? "/auth" : "");
  }

  public boolean secure() {
    return !Boolean.FALSE.equals(secureCookies);
  }

  public List<String> origins(boolean admin) {
    var list = admin ? adminOrigins : serviceOrigins;
    return list == null
        ? List.of(admin ? "https://admin.rallit.co.kr" : "https://rallit.co.kr")
        : List.copyOf(list);
  }
}
