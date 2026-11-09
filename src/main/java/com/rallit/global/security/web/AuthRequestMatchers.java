package com.rallit.global.security.web;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.model.OAuthProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumMap;
import org.springframework.security.web.util.matcher.*;

/** Uses the same decoded-path semantics as Spring Security and MVC. */
public final class AuthRequestMatchers {
  private static final RequestMatcher ADMIN = pathPattern("/api/v1/admin/**");
  private static final EnumMap<OAuthProvider, RequestMatcher> LOGIN =
      new EnumMap<>(OAuthProvider.class);
  private static final EnumMap<OAuthProvider, RequestMatcher> CALLBACK =
      new EnumMap<>(OAuthProvider.class);
  private static final EnumMap<LoginTarget, RequestMatcher> RECOVERY =
      new EnumMap<>(LoginTarget.class);

  static {
    for (OAuthProvider provider : OAuthProvider.values()) {
      LOGIN.put(provider, pathPattern("/api/v1/auth/" + provider.registrationId()));
      CALLBACK.put(
          provider, pathPattern("/api/v1/auth/" + provider.registrationId() + "/callback"));
    }
    for (LoginTarget target : LoginTarget.values())
      RECOVERY.put(
          target,
          new OrRequestMatcher(
              pathPattern(target.apiPath() + "/auth/refresh"),
              pathPattern(target.apiPath() + "/auth/logout")));
  }

  private AuthRequestMatchers() {}

  public static boolean isAdmin(HttpServletRequest request) {
    return ADMIN.matches(request);
  }

  public static boolean isRecovery(HttpServletRequest request, LoginTarget target) {
    return RECOVERY.get(target).matches(request);
  }

  public static boolean isLogin(HttpServletRequest request) {
    return LOGIN.values().stream().anyMatch(m -> m.matches(request));
  }

  public static boolean isOAuth(HttpServletRequest request) {
    return isLogin(request) || CALLBACK.values().stream().anyMatch(m -> m.matches(request));
  }

  public static boolean isCallback(HttpServletRequest request, String registrationId) {
    try {
      return CALLBACK.get(OAuthProvider.fromRegistrationId(registrationId)).matches(request);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }
}
