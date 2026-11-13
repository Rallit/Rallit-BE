package com.rallit.global.security.oauth2;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class GoogleAuthorizationIntegrationTest extends IntegrationTestSupport {
  @Autowired MockMvc mvc;

  @Test
  void googleStartsBackendOidcFlowWithEmailAndNonce() throws Exception {
    var result =
        mvc.perform(get("/api/v1/auth/google").param("target", "service"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    String location = result.getResponse().getHeader("Location");
    assertThat(location)
        .startsWith("https://accounts.google.com/")
        .contains("state=", "nonce=", "scope=openid%20email%20profile");
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

  @Test
  void googleStateCannotBeConsumedAtKakaoCallback() throws Exception {
    var response =
        mvc.perform(get("/api/v1/auth/google"))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getResponse();
    String state =
        java.util.Arrays.stream(
                java.net.URI.create(response.getHeader("Location")).getRawQuery().split("&"))
            .filter(q -> q.startsWith("state="))
            .map(
                q ->
                    java.net.URLDecoder.decode(
                        q.substring(6), java.nio.charset.StandardCharsets.UTF_8))
            .findFirst()
            .orElseThrow();
    var header =
        response.getHeaders("Set-Cookie").stream()
            .filter(h -> h.contains("rallit_oauth_"))
            .findFirst()
            .orElseThrow()
            .split(";", 2)[0]
            .split("=", 2);
    mvc.perform(
            get("/api/v1/auth/kakao/callback")
                .param("state", state)
                .param("code", "test-code")
                .cookie(new jakarta.servlet.http.Cookie(header[0], header[1])))
        .andExpect(redirectedUrl("https://rallit.co.kr/auth/callback?error=login_failed"));
    assertThat(
            redis.hasKey(
                "auth:oauth:" + com.rallit.domain.auth.service.TokenSessionService.hash(state)))
        .isTrue();
  }
}
