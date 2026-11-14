package com.rallit.global.security.oauth2;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.model.*;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.enums.SocialProvider;
import com.rallit.domain.user.repository.SocialAccountRepository;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.support.IntegrationTestSupport;
import com.sun.net.httpserver.*;
import jakarta.servlet.http.Cookie;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GoogleProtocolIntegrationTest extends IntegrationTestSupport {
  static final RSAKey key;
  static final RSAKey wrongKey;
  static final HttpServer server;
  static final Map<String, Exchange> codes = new ConcurrentHashMap<>();
  static final Map<String, Exchange> accessTokens = new ConcurrentHashMap<>();
  static final String CALLBACK = "https://rallit.co.kr/auth/callback";
  static final String DEV_CALLBACK = "https://dev.rallit.co.kr/auth/callback";

  record Exchange(String subject, String email, boolean verified, String nonce, String invalid) {}

  record Attempt(String state, String nonce, Cookie cookie) {}

  static {
    try {
      key = new RSAKeyGenerator(2048).keyID("test-key").generate();
      wrongKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/jwks", e -> reply(e, new JWKSet(key.toPublicJWK()).toString()));
      server.createContext(
          "/userinfo",
          e -> {
            var exchange = accessTokens.remove(e.getRequestHeaders().getFirst("Authorization"));
            if (exchange == null) {
              e.sendResponseHeaders(401, -1);
              e.close();
              return;
            }
            reply(e, "{\"sub\":\"" + exchange.subject() + "\"}");
          });
      server.createContext(
          "/token",
          e -> {
            try {
              Map<String, String> form =
                  query(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
              Exchange exchange = codes.remove(form.get("code"));
              if (exchange == null || !"authorization_code".equals(form.get("grant_type"))) {
                e.sendResponseHeaders(400, -1);
                e.close();
                return;
              }
              var claims =
                  new JWTClaimsSet.Builder()
                      .subject(exchange.subject())
                      .issuer(
                          "issuer".equals(exchange.invalid())
                              ? "https://attacker.invalid"
                              : "https://accounts.google.com")
                      .audience(
                          "audience".equals(exchange.invalid())
                              ? "wrong-client"
                              : "test-google-client")
                      .issueTime(Date.from(Instant.now().minusSeconds(10)))
                      .expirationTime(
                          Date.from(
                              Instant.now()
                                  .plusSeconds("expiry".equals(exchange.invalid()) ? -120 : 300)))
                      .claim(
                          "nonce",
                          "nonce".equals(exchange.invalid()) ? "wrong-nonce" : exchange.nonce())
                      .claim("email_verified", exchange.verified());
              if (exchange.email() != null) claims.claim("email", exchange.email());
              if (exchange.subject().startsWith("profile-")) claims.claim("name", "Google Name");
              var jwt =
                  new SignedJWT(
                      new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
                      claims.build());
              jwt.sign(new RSASSASigner("signature".equals(exchange.invalid()) ? wrongKey : key));
              String access = UUID.randomUUID().toString();
              accessTokens.put("Bearer " + access, exchange);
              reply(
                  e,
                  "{\"access_token\":\""
                      + access
                      + "\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"id_token\":\""
                      + jwt.serialize()
                      + "\"}");
            } catch (Exception failure) {
              e.sendResponseHeaders(500, -1);
              e.close();
            }
          });
      server.start();
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  static void reply(HttpExchange e, String body) throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    e.getResponseHeaders().set("Content-Type", "application/json");
    e.sendResponseHeaders(200, bytes.length);
    e.getResponseBody().write(bytes);
    e.close();
  }

  static Map<String, String> query(String value) {
    var result = new HashMap<String, String>();
    for (String item : value.split("&")) {
      var pair = item.split("=", 2);
      result.put(
          pair[0], URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8));
    }
    return result;
  }

  @DynamicPropertySource
  static void google(DynamicPropertyRegistry r) {
    r.add(
        "rallit.auth.google.token-uri",
        () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/token");
    r.add(
        "rallit.auth.google.user-info-uri",
        () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/userinfo");
    r.add(
        "rallit.auth.google.jwk-set-uri",
        () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
  }

  @Autowired MockMvc mvc;
  @Autowired SocialAccountRepository accounts;
  @Autowired UserRegistrationService registration;
  @Autowired TokenSessionService sessions;

  Attempt start(String target) throws Exception {
    return start(target, null);
  }

  Attempt start(String target, String callback) throws Exception {
    var request = get("/api/v1/auth/google").param("target", target);
    if (callback != null) request.param("callback", callback);
    var response =
        mvc.perform(request)
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getResponse();
    var params = query(URI.create(response.getHeader("Location")).getRawQuery());
    var binding =
        response.getHeaders("Set-Cookie").stream()
            .filter(v -> v.contains("rallit_oauth_"))
            .findFirst()
            .orElseThrow()
            .split(";", 2)[0]
            .split("=", 2);
    return new Attempt(
        params.get("state"), params.get("nonce"), new Cookie(binding[0], binding[1]));
  }

  org.springframework.mock.web.MockHttpServletResponse callback(
      Attempt attempt, String subject, String email, boolean verified, String invalid)
      throws Exception {
    String code = UUID.randomUUID().toString();
    codes.put(code, new Exchange(subject, email, verified, attempt.nonce(), invalid));
    var result =
        mvc.perform(
                get("/api/v1/auth/google/callback")
                    .param("code", code)
                    .param("state", attempt.state())
                    .cookie(attempt.cookie()))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(result.getRequest().getSession(false)).isNull();
    return result.getResponse();
  }

  @Test
  void signedOidcTokenCreatesMemberAndKeepsSameMemberOnRelogin() throws Exception {
    String subject = "profile-" + UUID.randomUUID(), email = subject + "@example.test";
    var first = callback(start("service", DEV_CALLBACK), subject, email, true, "");
    assertThat(first.getHeader("Location")).isEqualTo(DEV_CALLBACK);
    assertThat(first.getHeaders("Set-Cookie"))
        .anyMatch(c -> c.startsWith("__Host-rallit_refresh=") && !c.contains("Max-Age=0"));
    var member =
        accounts.findUserByProviderAndProviderUserId(SocialProvider.GOOGLE, subject).orElseThrow();
    assertThat(member.getEmail()).isEqualTo(email);
    String token = sessions.login(member.getId(), LoginTarget.SERVICE).accessToken();
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.socialDisplayName").value("Google Name"));
    assertThat(callback(start("service"), subject, email, true, "").getHeader("Location"))
        .isEqualTo(CALLBACK);
    assertThat(
            accounts
                .findUserByProviderAndProviderUserId(SocialProvider.GOOGLE, subject)
                .orElseThrow()
                .getId())
        .isEqualTo(member.getId());
  }

  @Test
  void invalidOidcEvidenceCannotCreateMemberOrSession() throws Exception {
    for (String invalid : List.of("signature", "issuer", "audience", "expiry", "nonce")) {
      String subject = UUID.randomUUID().toString();
      var result =
          callback(start("service", DEV_CALLBACK), subject, subject + "@example.test", true, invalid);
      assertThat(result.getHeader("Location"))
          .as(invalid)
          .isEqualTo(DEV_CALLBACK + "?error=login_failed");
      assertThat(result.getHeaders("Set-Cookie"))
          .noneMatch(c -> c.startsWith("__Host-rallit_refresh=") && !c.contains("Max-Age=0"));
      assertThat(accounts.findUserByProviderAndProviderUserId(SocialProvider.GOOGLE, subject)).isEmpty();
    }
  }

  @Test
  void collisionShowsExistingProviderAndDoesNotIssueRefreshCookie() throws Exception {
    String subject = UUID.randomUUID().toString(), email = subject + "@example.test";
    registration.getOrCreateUser(new SocialIdentity(OAuthProvider.KAKAO, subject, email, true));
    var response = callback(start("service", DEV_CALLBACK), subject, email, true, "");
    assertThat(response.getHeader("Location"))
        .isEqualTo(DEV_CALLBACK + "?error=account_exists&existingProvider=kakao");
    assertThat(accounts.findUserByProviderAndProviderUserId(SocialProvider.GOOGLE, subject)).isEmpty();
    assertThat(response.getHeaders("Set-Cookie"))
        .noneMatch(c -> c.startsWith("__Host-rallit_refresh=") && !c.contains("Max-Age=0"));
  }

  @Test
  void missingOrUnverifiedEmailIsRejected() throws Exception {
    for (boolean missing : List.of(true, false)) {
      String subject = UUID.randomUUID().toString();
      var result =
          callback(
              start("service"), subject, missing ? null : subject + "@example.test", false, "");
      assertThat(result.getHeader("Location")).isEqualTo(CALLBACK + "?error=email_required");
      assertThat(accounts.findUserByProviderAndProviderUserId(SocialProvider.GOOGLE, subject)).isEmpty();
    }
  }

  @Test
  void unknownGoogleAdminCannotRegisterEvenWithExistingAdminEmail() throws Exception {
    String subject = UUID.randomUUID().toString(), email = subject + "@example.test";
    var member =
        registration.getOrCreateUser(new SocialIdentity(OAuthProvider.KAKAO, subject, email, true));
    jdbc.update("update users set role='ADMIN' where id=?", member.getId());
    var result = callback(start("admin", DEV_CALLBACK), subject, email, true, "");
    assertThat(result.getHeader("Location"))
        .isEqualTo("https://admin.rallit.co.kr/auth/callback?error=login_failed");
    assertThat(accounts.findUserByProviderAndProviderUserId(SocialProvider.GOOGLE, subject)).isEmpty();
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
}
