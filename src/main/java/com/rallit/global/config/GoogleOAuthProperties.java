package com.rallit.global.config;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

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

  void validateTokenUri(Environment environment) {
    if (tokenUri == null) return;

    var uri = URI.create(tokenUri);
    String host = uri.getHost();
    if (host != null && uri.getUserInfo() == null) {
      if ("https".equalsIgnoreCase(uri.getScheme())) return;
      if ("http".equalsIgnoreCase(uri.getScheme())
          && Set.of("localhost", "127.0.0.1", "[::1]").contains(host.toLowerCase(Locale.ROOT))
          && environment.acceptsProfiles(Profiles.of("test & !prod & !temp"))) return;
    }
    throw new IllegalArgumentException("Google token URI must use HTTPS outside loopback tests");
  }

  @Override
  public String toString() {
    return "GoogleOAuthProperties[redacted]";
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
