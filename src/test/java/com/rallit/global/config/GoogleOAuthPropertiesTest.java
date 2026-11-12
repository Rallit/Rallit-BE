package com.rallit.global.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class GoogleOAuthPropertiesTest {
  @Test
  void tokenUriRequiresHttpsInDeploymentProfiles() {
    var properties = properties("http://127.0.0.1:8080/token");
    for (String deploymentProfile : new String[] {"prod", "temp"}) {
      var environment = new MockEnvironment();
      environment.setActiveProfiles("test", deploymentProfile);
      assertThatThrownBy(() -> properties.validateTokenUri(environment))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("HTTPS");
      assertThatCode(() -> properties("https://oauth.example.test/token").validateTokenUri(environment))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void testsCanUseHttpOnlyOnLoopback() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("test");
    assertThatCode(() -> properties("http://127.0.0.1:8080/token").validateTokenUri(environment))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> properties("http://oauth.example.test/token").validateTokenUri(environment))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private GoogleOAuthProperties properties(String tokenUri) {
    return new GoogleOAuthProperties(
        "test-client", "test-secret", "https://example.test/callback", null, tokenUri, null, null);
  }
}
