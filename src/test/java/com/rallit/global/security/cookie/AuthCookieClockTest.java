package com.rallit.global.security.cookie;

import static org.assertj.core.api.Assertions.*;

import com.rallit.domain.auth.dto.internal.SessionTokens;
import com.rallit.domain.auth.model.*;
import com.rallit.global.config.AuthProperties;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieClockTest {
  @Test
  void cookieLifetimeUsesTheSameFixedClockAsTokens() {
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var properties =
        new AuthProperties(
            "unused",
            "other-unused",
            "https://rallit.co.kr/auth/callback",
            "https://admin.rallit.co.kr/auth/callback",
            "client",
            null,
            "https://api.rallit.co.kr/api/v1/auth/kakao/callback",
            null,
            null,
            null,
            true,
            null,
            null);
    var cookies = new AuthCookieManager(properties, Clock.fixed(now, ZoneOffset.UTC));
    var response = new MockHttpServletResponse();
    cookies.writeRefresh(
        response,
        new SessionTokens(
            "synthetic-access",
            "synthetic-refresh",
            now.plusSeconds(1800),
            now.plusSeconds(1209600)),
        LoginTarget.SERVICE);
    assertThat(response.getHeaders("Set-Cookie"))
        .anySatisfy(v -> assertThat(v).contains("__Host-rallit_access=", "Max-Age=0"));
    assertThat(response.getHeaders("Set-Cookie"))
        .anySatisfy(v -> assertThat(v).contains("__Host-rallit_refresh=", "Max-Age=1209600"));
  }
}
