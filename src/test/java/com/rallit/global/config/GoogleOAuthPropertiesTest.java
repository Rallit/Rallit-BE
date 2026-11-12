package com.rallit.global.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class GoogleOAuthPropertiesTest {
  @Test
  void tokenUriRequiresHttpsInDeploymentProfiles() {
    var properties = properties("http://127.0.0.1:8080/token", null);
    for (String deploymentProfile : new String[] {"prod", "temp"}) {
      var environment = new MockEnvironment();
      environment.setActiveProfiles("test", deploymentProfile);
      assertThatThrownBy(() -> properties.validateTokenAndUserInfoUris(environment))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("HTTPS");
      assertThatCode(
              () ->
                  properties("https://oauth.example.test/token", null)
                      .validateTokenAndUserInfoUris(environment))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void testsCanUseHttpOnlyOnLoopback() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("test");
    assertThatCode(
            () ->
                properties("http://127.0.0.1:8080/token", null)
                    .validateTokenAndUserInfoUris(environment))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                properties("http://oauth.example.test/token", null)
                    .validateTokenAndUserInfoUris(environment))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void userInfoUriRequiresHttpsInDeploymentProfiles() {
    var properties = properties(null, "http://127.0.0.1:8080/userinfo");
    for (String deploymentProfile : new String[] {"prod", "temp"}) {
      var environment = new MockEnvironment();
      environment.setActiveProfiles("test", deploymentProfile);
      assertThatThrownBy(() -> properties.validateTokenAndUserInfoUris(environment))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("UserInfo")
          .hasMessageContaining("HTTPS");
      assertThatCode(
              () ->
                  properties(null, "https://oauth.example.test/userinfo")
                      .validateTokenAndUserInfoUris(environment))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void userInfoUriAllowsHttpOnlyOnTestLoopback() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("test");
    assertThatCode(
            () ->
                properties(null, "http://127.0.0.1:8080/userinfo")
                    .validateTokenAndUserInfoUris(environment))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                properties(null, "http://oauth.example.test/userinfo")
                    .validateTokenAndUserInfoUris(environment))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private GoogleOAuthProperties properties(String tokenUri, String userInfoUri) {
    return new GoogleOAuthProperties(
        "test-client",
        "test-secret",
        "https://example.test/callback",
        null,
        tokenUri,
        null,
        userInfoUri);
  }
}
