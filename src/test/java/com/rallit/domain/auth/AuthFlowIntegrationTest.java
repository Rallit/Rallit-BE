package com.rallit.domain.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rallit.domain.auth.model.*;
import com.rallit.domain.auth.service.*;
import com.rallit.domain.user.service.*;
import com.rallit.global.security.jwt.JwtTokenService;
import com.rallit.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends IntegrationTestSupport {
  @Autowired UserRegistrationService users;
  @Autowired TokenSessionService sessions;
  @Autowired JwtTokenService jwt;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;

  long user() {
    return users.getOrCreateUser(kakao(UUID.randomUUID().toString())).getId();
  }

  @Test
  void registrationIsStableAndCaseInsensitiveUnique() {
    String id = UUID.randomUUID().toString();
    var a = users.getOrCreateUser(kakao(id));
    var b = users.getOrCreateUser(kakao(id));
    assertThat(a.getId()).isEqualTo(b.getId());
    assertThat(a.getNickname()).isEqualTo(b.getNickname());
    assertThat(a.getNickname()).matches("[가-힣a-z0-9]{2,12}");
  }

  @Test
  void accessLifetimeAndAudienceAreEnforced() {
    long id = user();
    var pair = sessions.login(id, LoginTarget.SERVICE);
    assertThat(Duration.between(Instant.now(), pair.accessExpiresAt()).toMinutes())
        .isBetween(29L, 30L);
    assertThat(jwt.verifyAccess(pair.accessToken(), LoginTarget.SERVICE).userId()).isEqualTo(id);
    assertThatThrownBy(() -> jwt.verifyAccess(pair.accessToken(), LoginTarget.ADMIN))
        .isInstanceOfSatisfying(
            com.rallit.global.apiPayload.exception.GeneralException.class,
            failure ->
                assertThat(failure.getCode())
                    .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED));
    assertThatThrownBy(() -> jwt.verifyAccess(pair.refreshToken(), LoginTarget.SERVICE))
        .isInstanceOfSatisfying(
            com.rallit.global.apiPayload.exception.GeneralException.class,
            failure ->
                assertThat(failure.getCode())
                    .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED));
  }

  @Test
  void rotationPreservesDeadlineAndOldTokenCannotRevokeNewSession() {
    var original = sessions.login(user(), LoginTarget.SERVICE);
    var rotated = sessions.refresh(original.refreshToken(), LoginTarget.SERVICE);
    assertThat(rotated.sessionExpiresAt()).isEqualTo(original.sessionExpiresAt());
    assertThatThrownBy(() -> sessions.refresh(original.refreshToken(), LoginTarget.SERVICE))
        .isInstanceOfSatisfying(
            com.rallit.global.apiPayload.exception.GeneralException.class,
            failure ->
                assertThat(failure.getCode())
                    .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED));
    assertThatThrownBy(() -> sessions.logout(original.refreshToken(), LoginTarget.SERVICE))
        .isInstanceOfSatisfying(
            com.rallit.global.apiPayload.exception.GeneralException.class,
            failure ->
                assertThat(failure.getCode())
                    .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED));
    assertThat(sessions.refresh(rotated.refreshToken(), LoginTarget.SERVICE)).isNotNull();
  }

  @Test
  void concurrentRefreshHasOneWinner() throws Exception {
    var pair = sessions.login(user(), LoginTarget.SERVICE);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var latch = new CountDownLatch(1);
      Callable<Boolean> call =
          () -> {
            latch.await();
            try {
              sessions.refresh(pair.refreshToken(), LoginTarget.SERVICE);
              return true;
            } catch (com.rallit.global.apiPayload.exception.GeneralException e) {
              assertThat(e.getCode())
                  .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED);
              return false;
            }
          };
      var a = pool.submit(call);
      var b = pool.submit(call);
      latch.countDown();
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
    }
  }

  @Test
  void sessionsLogoutIndependently() {
    long id = user();
    var a = sessions.login(id, LoginTarget.SERVICE);
    var b = sessions.login(id, LoginTarget.SERVICE);
    sessions.logout(a.refreshToken(), LoginTarget.SERVICE);
    assertThatThrownBy(() -> sessions.refresh(a.refreshToken(), LoginTarget.SERVICE))
        .isInstanceOfSatisfying(
            com.rallit.global.apiPayload.exception.GeneralException.class,
            failure ->
                assertThat(failure.getCode())
                    .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED));
    assertThat(sessions.refresh(b.refreshToken(), LoginTarget.SERVICE)).isNotNull();
  }

  @Test
  void pendingUserCanReadMeButNoTokensInBody() throws Exception {
    var pair = sessions.login(user(), LoginTarget.SERVICE);
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + pair.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.onboardingCompleted").value(false))
        .andExpect(jsonPath("$.result.accessToken").doesNotExist())
        .andExpect(jsonPath("$.result.profile").doesNotExist());
  }

  @Test
  void unauthenticatedAndWrongPurposeFail() throws Exception {
    mvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false));
    var pair = sessions.login(user(), LoginTarget.SERVICE);
    mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + pair.accessToken()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminDoesNotNeedTennisProfileButRoleRemovalIsImmediate() throws Exception {
    long id = user();
    jdbc.update("update users set role='ADMIN' where id=?", id);
    var pair = sessions.login(id, LoginTarget.ADMIN);
    mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + pair.accessToken()))
        .andExpect(status().isOk());
    jdbc.update("update users set role='USER' where id=?", id);
    mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + pair.accessToken()))
        .andExpect(status().isForbidden());
    assertThatThrownBy(() -> sessions.refresh(pair.refreshToken(), LoginTarget.ADMIN))
        .isInstanceOfSatisfying(
            com.rallit.global.apiPayload.exception.GeneralException.class,
            failure ->
                assertThat(failure.getCode())
                    .isEqualTo(com.rallit.global.apiPayload.code.status.ErrorStatus._FORBIDDEN));
  }

  @Test
  void cookieOperationsRequireGuardsAndCorsIsSeparated() throws Exception {
    mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isForbidden());
    mvc.perform(
            options("/api/v1/admin/me")
                .header("Origin", "https://rallit.co.kr")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
    mvc.perform(
            options("/api/v1/admin/me")
                .header("Origin", "https://admin.rallit.co.kr")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk());
  }

  @Test
  void loginRedirectStartsAtKakaoAndNeverAcceptsUnknownTarget() throws Exception {
    mvc.perform(get("/api/v1/auth/kakao").param("target", "evil"))
        .andExpect(status().isBadRequest());
    var result =
        mvc.perform(get("/api/v1/auth/kakao").param("target", "service"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(result.getResponse().getHeader("Location"))
        .startsWith("https://kauth.kakao.com/oauth/authorize?")
        .contains("state=");
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  void invalidStateDoesNotRedirectToAttacker() throws Exception {
    mvc.perform(
            get("/api/v1/auth/kakao/callback")
                .param("code", "dummy")
                .param("state", "bad")
                .param("redirect", "https://evil.example"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("https://rallit.co.kr/auth/callback?error=login_failed"));
  }

  @Autowired tools.jackson.databind.ObjectMapper mapper;

  @Test
  void unsafeAdminRequestsRequireAllowedOrigin() throws Exception {
    long id = user();
    jdbc.update("update users set role='ADMIN' where id=?", id);
    var pair = sessions.login(id, LoginTarget.ADMIN);
    var cookie = new Cookie("__Host-rallit_admin_refresh", pair.refreshToken());
    mvc.perform(
            post("/api/v1/admin/auth/refresh")
                .cookie(cookie)
                .contentType("application/json")
                .header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/admin/auth/refresh")
                .cookie(cookie)
                .contentType("application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://rallit.co.kr"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/admin/auth/refresh")
                .cookie(cookie)
                .contentType("application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://admin.rallit.co.kr"))
        .andExpect(status().isOk());
  }

  @Test
  void ambiguousSensitiveCookiesAreRejected() throws Exception {
    mvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(
                    new Cookie("__Host-rallit_refresh", "one"),
                    new Cookie("__Host-rallit_refresh", "two"))
                .contentType("application/json")
                .header("Origin", "https://rallit.co.kr")
                .header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isUnauthorized());
  }

  @Autowired com.rallit.domain.user.repository.UserRepository repository;

  @Test
  void redisUnavailableReturnsServiceUnavailableWithoutIssuingSession() throws Exception {
    int port;
    try (var socket = new java.net.ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var client =
        org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
            .commandTimeout(java.time.Duration.ofMillis(200))
            .clientOptions(
                io.lettuce.core.ClientOptions.builder()
                    .socketOptions(
                        io.lettuce.core.SocketOptions.builder()
                            .connectTimeout(java.time.Duration.ofMillis(200))
                            .build())
                    .build())
            .build();
    var factory =
        new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
            new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                "127.0.0.1", port),
            client);
    factory.afterPropertiesSet();
    factory.start();
    try {
      var service =
          new TokenSessionService(
              jwt,
              new org.springframework.data.redis.core.StringRedisTemplate(factory),
              repository,
              java.time.Clock.systemUTC());
      assertThatThrownBy(() -> service.login(user(), LoginTarget.SERVICE))
          .isInstanceOfSatisfying(
              com.rallit.global.apiPayload.exception.GeneralException.class,
              e -> assertThat(e.getErrorReasonHttpStatus().getHttpStatus().value()).isEqualTo(503));
    } finally {
      factory.destroy();
    }
  }
}
