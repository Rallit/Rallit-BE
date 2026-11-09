package com.rallit.global.security.web;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import com.rallit.domain.auth.model.LoginTarget;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumMap;
import org.springframework.security.web.util.matcher.*;

/** Uses the same decoded-path semantics as Spring Security and MVC. */
public final class AuthRequestMatchers {
  private static final RequestMatcher ADMIN = pathPattern("/api/v1/admin/**");
  private static final RequestMatcher KAKAO_LOGIN = pathPattern("/api/v1/auth/kakao");
  private static final RequestMatcher KAKAO_CALLBACK = pathPattern("/api/v1/auth/kakao/callback");
  private static final EnumMap<LoginTarget, RequestMatcher> RECOVERY =
      new EnumMap<>(LoginTarget.class);

  static {
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

  public static boolean isKakaoLogin(HttpServletRequest request) {
    return KAKAO_LOGIN.matches(request);
  }

  public static boolean isOAuth(HttpServletRequest request) {
    return KAKAO_LOGIN.matches(request) || KAKAO_CALLBACK.matches(request);
  }
}
