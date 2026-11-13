package com.rallit.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.support.IntegrationTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "RALLIT_ENV_FILE=.env.test-disabled")
class ProfileImageMultipartIntegrationTest extends IntegrationTestSupport {
  @LocalServerPort int port;
  @Autowired UserRegistrationService users;
  @Autowired TokenSessionService sessions;
  @Autowired ObjectMapper json;

  @Test
  void multipartParserReturnsTheFileSizeErrorForAnOversizedPhoto() throws Exception {
    var user = users.getOrCreateUser(kakao(UUID.randomUUID().toString()));
    var token = sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
    String boundary = "profile-image-test";
    var request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/api/v1/users/me/profile-image"))
        .timeout(Duration.ofSeconds(10))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .PUT(BodyPublishers.concat(
            BodyPublishers.ofString("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"avatar.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n"),
            BodyPublishers.ofByteArray(new byte[5 * 1024 * 1024 + 1]),
            BodyPublishers.ofString("\r\n--" + boundary + "--\r\n")))
        .build();

    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      assertThat(response.statusCode()).isEqualTo(413);
      var body = json.readTree(response.body());
      assertThat(body.path("isSuccess").asBoolean()).isFalse();
      assertThat(body.path("code").asText()).isEqualTo("COMMON413");
      assertThat(body.path("message").asText()).isEqualTo("파일은 최대 5MB까지 올릴 수 있습니다.");
    }
  }
}
