package com.rallit.global.security;

import static org.assertj.core.api.Assertions.*;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.global.security.config.AuthProperties;
import com.rallit.global.security.jwt.JwtTokenService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SecurityPolicyTest {
  AuthProperties properties(String callback, Boolean secure) {
    return new AuthProperties(
        Base64.getEncoder().encodeToString(new byte[32]),
        Base64.getEncoder().encodeToString("a-different-test-signing-key-32-bytes!!".getBytes()),
        callback,
        secure ? "https://admin.rallit.co.kr/auth/callback" : "http://localhost:3001/auth/callback",
        "client",
        null,
        "http://localhost:8080/api/v1/auth/kakao/callback",
        null,
        null,
        null,
        secure,
        null,
        null);
  }

  @Test
  void invalidProductionCallbacksFailBeforeAuthentication() {
    for (String url :
        List.of(
            "https://evil.example/callback",
            "http://rallit.co.kr/callback",
            "https://rallit.co.kr:8443/callback",
            "https://rallit.co.kr/callback?next=evil",
            "javascript:alert(1)"))
      assertThatThrownBy(() -> properties(url, true)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void productionCookiesHaveHostPrefixAndLocalCanUseHttp() {
    var prod = properties("https://rallit.co.kr/auth/callback", true);
    assertThat(prod.cookieName(LoginTarget.ADMIN, "access"))
        .isEqualTo("__Host-rallit_admin_access");
    assertThat(prod.cookiePath(LoginTarget.ADMIN, true)).isEqualTo("/");
    var local = properties("http://localhost:3000/auth/callback", false);
    assertThat(local.cookieName(LoginTarget.SERVICE, "access")).isEqualTo("rallit_access");
    assertThat(local.secure()).isFalse();
  }

  @Test
  void accessExpiryAndSessionDeadlineAreExact() {
    Instant now = Instant.parse("2026-11-08T00:00:00Z");
    var p = properties("https://rallit.co.kr/auth/callback", true);
    var issuer = new JwtTokenService(p, Clock.fixed(now, ZoneOffset.UTC));
    var issued =
        issuer.issue(1L, UUID.randomUUID(), LoginTarget.SERVICE, now.plusSeconds(14 * 86400));
    var later = new JwtTokenService(p, Clock.fixed(now.plusSeconds(1800), ZoneOffset.UTC));
    assertThatThrownBy(() -> later.verifyAccess(issued.accessToken(), LoginTarget.SERVICE))
        .isInstanceOfSatisfying(
            com.rallit.global.apiPayload.exception.GeneralException.class,
            failure ->
                assertThat(failure.getCode())
                    .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED));
    assertThat(later.verifyRefresh(issued.refreshToken(), LoginTarget.SERVICE).userId())
        .isEqualTo(1L);
    var last = issuer.issue(1L, UUID.randomUUID(), LoginTarget.SERVICE, now.plusSeconds(60));
    assertThat(last.accessExpiresAt()).isEqualTo(now.plusSeconds(60));
    var split = issued.accessToken().split("\\.");
    split[1] =
        Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"99\"}".getBytes());
    assertThatThrownBy(() -> issuer.verifyAccess(String.join(".", split), LoginTarget.SERVICE))
        .isInstanceOfSatisfying(
            com.rallit.global.apiPayload.exception.GeneralException.class,
            failure ->
                assertThat(failure.getCode())
                    .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED));
  }

  @Test
  void malformedJwtPreservesItsCauseWithoutChangingExternalError() {
    var service =
        new JwtTokenService(
            properties("https://rallit.co.kr/auth/callback", true), Clock.systemUTC());
    assertThatThrownBy(() -> service.verifyAccess("malformed-test-jwt", LoginTarget.SERVICE))
        .isInstanceOfSatisfying(
            com.rallit.global.apiPayload.exception.GeneralException.class,
            failure -> {
              assertThat(failure.getCode())
                  .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED);
              assertThat(failure.getCause())
                  .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
            });
  }
}
