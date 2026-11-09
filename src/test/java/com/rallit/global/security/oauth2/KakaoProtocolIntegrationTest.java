package com.rallit.global.security.oauth2;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rallit.support.IntegrationTestSupport;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class KakaoProtocolIntegrationTest extends IntegrationTestSupport {
  static final HttpServer server;
  static final AtomicInteger exchanges = new AtomicInteger();

  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/token",
          e -> {
            exchanges.incrementAndGet();
            String form = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!form.contains("grant_type=authorization_code")
                || !form.contains("client_id=test-client")
                || !form.contains("code_verifier=")) {
              e.sendResponseHeaders(400, -1);
              e.close();
              return;
            }
            byte[] body =
                "{\"access_token\":\"test-kakao-access\",\"token_type\":\"bearer\",\"expires_in\":3600}"
                    .getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type", "application/json");
            e.sendResponseHeaders(200, body.length);
            e.getResponseBody().write(body);
            e.close();
          });
      server.createContext(
          "/userinfo",
          e -> {
            if (!"Bearer test-kakao-access"
                .equals(e.getRequestHeaders().getFirst("Authorization"))) {
              e.sendResponseHeaders(401, -1);
              e.close();
              return;
            }
            byte[] body =
                "{\"id\":12345678,\"connected_at\":\"2026-01-01T00:00:00Z\"}"
                    .getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type", "application/json");
            e.sendResponseHeaders(200, body.length);
            e.getResponseBody().write(body);
            e.close();
          });
      server.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void provider(DynamicPropertyRegistry r) {
    r.add(
        "rallit.auth.kakao-token-uri",
        () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/token");
    r.add(
        "rallit.auth.kakao-user-info-uri",
        () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/userinfo");
  }

  @Autowired MockMvc mvc;
  @Autowired tools.jackson.databind.ObjectMapper mapper;

  record Attempt(String state, Cookie binding) {}

  Attempt start(String target) throws Exception {
    var response =
        mvc.perform(get("/api/v1/auth/kakao").param("target", target))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getResponse();
    String state =
        Arrays.stream(URI.create(response.getHeader("Location")).getRawQuery().split("&"))
            .filter(v -> v.startsWith("state="))
            .map(v -> URLDecoder.decode(v.substring(6), StandardCharsets.UTF_8))
            .findFirst()
            .orElseThrow();
    var binding =
        Arrays.stream(response.getHeaders("Set-Cookie").toArray(String[]::new))
            .map(v -> v.split(";", 2)[0].split("=", 2))
            .filter(v -> v[0].contains("rallit_oauth_"))
            .map(v -> new Cookie(v[0], v[1]))
            .findFirst()
            .orElseThrow();
    return new Attempt(state, binding);
  }

  @Test
  void realOAuthFiltersExchangeCodeAndBindToOriginalTarget() throws Exception {
    var a = start("service");
    int before = exchanges.get();
    var response =
        mvc.perform(
                get("/api/v1/auth/kakao/callback")
                    .param("code", "test-code")
                    .param("state", a.state())
                    .param("target", "admin")
                    .cookie(a.binding()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("https://rallit.co.kr/auth/callback"))
            .andReturn()
            .getResponse();
    assertThat(exchanges.get()).isEqualTo(before + 1);
    assertThat(response.getHeaders("Set-Cookie"))
        .anySatisfy(
            c ->
                assertThat(c)
                    .startsWith("__Host-rallit_refresh=")
                    .contains("HttpOnly", "Secure", "SameSite=Lax"));
    assertThat(response.getHeaders("Set-Cookie"))
        .noneMatch(c -> c.startsWith("__Host-rallit_admin_refresh="));
    mvc.perform(
            get("/api/v1/auth/kakao/callback")
                .param("code", "test-code")
                .param("state", a.state())
                .cookie(a.binding()))
        .andExpect(redirectedUrl("https://rallit.co.kr/auth/callback?error=login_failed"));
  }

  @Test
  void wrongBrowserDoesNotConsumeLegitimateState() throws Exception {
    var a = start("service");
    mvc.perform(
            get("/api/v1/auth/kakao/callback")
                .param("code", "test-code")
                .param("state", a.state())
                .cookie(new Cookie(a.binding().getName(), "wrong")))
        .andExpect(redirectedUrl("https://rallit.co.kr/auth/callback?error=login_failed"));
    mvc.perform(
            get("/api/v1/auth/kakao/callback")
                .param("code", "test-code")
                .param("state", a.state())
                .cookie(a.binding()))
        .andExpect(redirectedUrl("https://rallit.co.kr/auth/callback"));
  }

  @Test
  void refreshSuppliesMemoryAccessTokenAndLogoutNeedsNoCsrfEndpoint() throws Exception {
    var attempt = start("service");
    var logged =
        mvc.perform(
                get("/api/v1/auth/kakao/callback")
                    .param("code", "test-code")
                    .param("state", attempt.state())
                    .cookie(attempt.binding()))
            .andExpect(redirectedUrl("https://rallit.co.kr/auth/callback"))
            .andReturn()
            .getResponse();
    Cookie refresh = cookie(logged.getHeaders("Set-Cookie"), "__Host-rallit_refresh");
    assertThat(logged.getHeaders("Set-Cookie"))
        .allMatch(
            v ->
                v.contains("rallit_refresh=")
                    || v.contains("rallit_oauth_")
                    || v.contains("Max-Age=0"));
    var rotated =
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .cookie(refresh, new Cookie("__Host-rallit_access", "retired-cookie"))
                    .header("Origin", "https://rallit.co.kr")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .contentType("application/json")
                    .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.refreshToken").doesNotExist())
            .andReturn()
            .getResponse();
    String access =
        mapper.readTree(rotated.getContentAsString()).path("result").path("accessToken").asText();
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
    Cookie nextRefresh = cookie(rotated.getHeaders("Set-Cookie"), "__Host-rallit_refresh");
    mvc.perform(
            post("/api/v1/auth/logout")
                .cookie(nextRefresh)
                .header("Origin", "https://rallit.co.kr")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/json"))
        .andExpect(status().isNoContent());
  }

  Cookie cookie(Collection<String> headers, String name) {
    return headers.stream()
        .map(v -> v.split(";", 2)[0].split("=", 2))
        .filter(v -> v[0].equals(name))
        .map(v -> new Cookie(name, v[1]))
        .findFirst()
        .orElseThrow();
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired com.rallit.domain.user.service.UserRegistrationService registration;

  @Test
  void adminLoginHasSeparateCookiesAndRequiresCurrentRole() throws Exception {
    long id = registration.getOrCreateKakaoUser("12345678").getId();
    jdbc.update("update users set role='USER' where id=?", id);
    var denied = start("admin");
    var failure =
        mvc.perform(
                get("/api/v1/auth/kakao/callback")
                    .param("code", "test-code")
                    .param("state", denied.state())
                    .cookie(
                        denied.binding(),
                        new Cookie("__Host-rallit_csrf", "existing-service-token")))
            .andExpect(redirectedUrl("https://admin.rallit.co.kr/auth/callback?error=login_failed"))
            .andReturn()
            .getResponse();
    assertThat(failure.getHeaders("Set-Cookie"))
        .noneMatch(c -> c.startsWith("__Host-rallit_csrf="));
    assertThat(failure.getHeaders("Set-Cookie"))
        .noneMatch(c -> c.startsWith("__Host-rallit_admin_refresh="));
    jdbc.update("update users set role='ADMIN' where id=?", id);
    try {
      var allowed = start("admin");
      var response =
          mvc.perform(
                  get("/api/v1/auth/kakao/callback")
                      .param("code", "test-code")
                      .param("state", allowed.state())
                      .cookie(allowed.binding()))
              .andExpect(redirectedUrl("https://admin.rallit.co.kr/auth/callback"))
              .andReturn()
              .getResponse();
      assertThat(response.getHeaders("Set-Cookie"))
          .anySatisfy(
              c ->
                  assertThat(c)
                      .startsWith("__Host-rallit_admin_refresh=")
                      .contains("Path=/", "HttpOnly", "Secure"));
      assertThat(response.getHeaders("Set-Cookie"))
          .noneMatch(c -> c.startsWith("__Host-rallit_refresh="));
    } finally {
      jdbc.update("update users set role='USER' where id=?", id);
    }
  }
}
