package com.rallit.domain.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rallit.domain.auth.model.*;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class BearerContractIntegrationTest extends IntegrationTestSupport {
  @Autowired TokenSessionService sessions;
  @Autowired UserRegistrationService users;
  @Autowired MockMvc mvc;
  @Autowired tools.jackson.databind.ObjectMapper json;

  SessionTokens login() {
    return sessions.login(
        users.getOrCreateKakaoUser(UUID.randomUUID().toString()).getId(), LoginTarget.SERVICE);
  }

  @Test
  void bearerIsRequiredAndAccessCookieIsNeverFallback() throws Exception {
    var pair = login();
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + pair.accessToken()))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/users/me").cookie(new Cookie("__Host-rallit_access", pair.accessToken())))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + pair.refreshToken()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/users/me")
                .header(
                    "Authorization",
                    "Bearer " + pair.accessToken(),
                    "Bearer " + pair.accessToken()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshReturnsAccessBodyAndOnlyStoresRefreshCookie() throws Exception {
    var pair = login();
    var response =
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .cookie(new Cookie("__Host-rallit_refresh", pair.refreshToken()))
                    .header("Origin", "https://rallit.co.kr")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .contentType("application/json")
                    .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("COMMON200"))
            .andExpect(jsonPath("$.result.accessToken").isString())
            .andExpect(jsonPath("$.result.refreshToken").doesNotExist())
            .andReturn()
            .getResponse();
    var access =
        json.readTree(response.getContentAsString()).path("result").path("accessToken").asText();
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
    assertThat(response.getHeaders("Set-Cookie"))
        .anySatisfy(
            v -> assertThat(v).startsWith("__Host-rallit_refresh=").contains("HttpOnly", "Secure"));
    assertThat(response.getHeaders("Set-Cookie"))
        .allMatch(v -> v.startsWith("__Host-rallit_refresh=") || v.contains("Max-Age=0"));
  }

  @Test
  void cookieOperationsRejectMissingOrWrongOriginHeaderAndSimpleContentTypes() throws Exception {
    var pair = login();
    var cookie = new Cookie("__Host-rallit_refresh", pair.refreshToken());
    for (String action : List.of("refresh", "logout")) {
      String path = "/api/v1/auth/" + action;
      mvc.perform(
              post(path)
                  .cookie(cookie)
                  .header("X-Requested-With", "XMLHttpRequest")
                  .contentType("application/json"))
          .andExpect(status().isForbidden());
      mvc.perform(
              post(path)
                  .cookie(cookie)
                  .header("Origin", "null")
                  .header("X-Requested-With", "XMLHttpRequest")
                  .contentType("application/json"))
          .andExpect(status().isForbidden());
      mvc.perform(
              post(path)
                  .cookie(cookie)
                  .header("Origin", "https://rallit.co.kr")
                  .contentType("application/json"))
          .andExpect(status().isForbidden());
      mvc.perform(
              post(path)
                  .cookie(cookie)
                  .header("Origin", "https://rallit.co.kr")
                  .header("X-Requested-With", "XMLHttpRequest")
                  .contentType("text/plain"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/api/v1/auth/logout")
                .cookie(cookie)
                .header("Origin", "https://rallit.co.kr")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/json"))
        .andExpect(status().isNoContent());
  }

  @Test
  void csrfEndpointIsNoLongerExposed() throws Exception {
    var pair = login();
    mvc.perform(get("/api/v1/auth/csrf").header("Authorization", "Bearer " + pair.accessToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void encodedRecoveryPathsCannotBypassCookieGuards() throws Exception {
    var pair = login();
    for (String path : List.of("/api/v1/auth/%72efresh", "/api/v1/auth/%6cogout")) {
      mvc.perform(
              post(java.net.URI.create(path))
                  .cookie(new Cookie("__Host-rallit_refresh", pair.refreshToken())))
          .andExpect(status().isForbidden());
    }
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Test
  void encodedAdminPathUsesAdminSessionNotServiceCookie() throws Exception {
    long id = users.getOrCreateKakaoUser(UUID.randomUUID().toString()).getId();
    jdbc.update("update users set role='ADMIN' where id=?", id);
    var admin = sessions.login(id, LoginTarget.ADMIN);
    var service = sessions.login(id, LoginTarget.SERVICE);
    mvc.perform(
            post(java.net.URI.create("/api/v1/%61dmin/auth/refresh"))
                .cookie(new Cookie("__Host-rallit_refresh", service.refreshToken()))
                .header("Origin", "https://admin.rallit.co.kr")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/json"))
        .andExpect(status().isUnauthorized());
    var response =
        mvc.perform(
                post(java.net.URI.create("/api/v1/%61dmin/auth/refresh"))
                    .cookie(new Cookie("__Host-rallit_admin_refresh", admin.refreshToken()))
                    .header("Origin", "https://admin.rallit.co.kr")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .contentType("application/json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    String access =
        json.readTree(response.getContentAsString()).path("result").path("accessToken").asText();
    mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isUnauthorized());
  }
}
