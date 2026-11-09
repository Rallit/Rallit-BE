package com.rallit.global.security.web;

import com.rallit.domain.auth.model.*;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.config.AuthProperties;
import jakarta.servlet.http.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieManager {
  private final AuthProperties properties;
  private final Clock clock;

  public String name(LoginTarget t, String kind) {
    return properties.cookieName(t, kind);
  }

  public String read(HttpServletRequest request, String name) {
    String found = null;
    if (request.getCookies() != null)
      for (var c : request.getCookies())
        if (c.getName().equals(name)) {
          if (found != null) throw new GeneralException(ErrorStatus._UNAUTHORIZED);
          found = c.getValue();
        }
    return found;
  }

  public void add(
      HttpServletResponse response, String name, String value, String path, long seconds) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(properties.secure())
            .sameSite("Lax")
            .path(path)
            .maxAge(Math.max(0, seconds))
            .build()
            .toString());
  }

  public void writeRefresh(HttpServletResponse response, SessionTokens tokens, LoginTarget target) {
    clearLegacyCookies(response, target);
    add(
        response,
        name(target, "refresh"),
        tokens.refreshToken(),
        properties.cookiePath(target, true),
        Duration.between(clock.instant(), tokens.sessionExpiresAt()).toSeconds());
  }

  public void clearLegacyCookies(HttpServletResponse response, LoginTarget target) {
    for (String path :
        properties.secure()
            ? java.util.Set.of("/")
            : new java.util.HashSet<>(java.util.List.of(target.apiPath(), "/"))) {
      add(response, name(target, "access"), "", path, 0);
      add(response, name(target, "csrf"), "", path, 0);
    }
  }

  public void clear(HttpServletResponse response, LoginTarget target) {
    add(response, name(target, "refresh"), "", properties.cookiePath(target, true), 0);
    clearLegacyCookies(response, target);
  }
}
