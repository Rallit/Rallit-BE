package com.rallit.domain.court.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.enums.Gender;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "RALLIT_ENV_FILE=.env.test-disabled",
    "rallit.court-thumbnail.storage-path=./build/court-thumbnail-http-integration"
})
class CourtThumbnailHttpIntegrationTest extends IntegrationTestSupport {
  @LocalServerPort int port;
  @Autowired EntityManager entities;
  @Autowired TransactionTemplate transactions;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenSessionService sessions;
  @Autowired ObjectMapper json;

  @ParameterizedTest
  @CsvSource({
      "476c7a5a-f683-4670-ab59-13590e71976f.png, image/png, 404, COURT_THUMBNAIL_NOT_FOUND",
      "../invalid.jpg, image/jpeg, 503, COMMON503"
  })
  void returnsTheJsonDomainErrorWhenTheClientAcceptsOnlyAnImage(
      String key, String accept, int expectedStatus, String expectedCode) throws Exception {
    var fixture = transactions.execute(status -> {
      var user = EntityFixtures.user(entities, "코트" + UUID.randomUUID().toString().substring(0, 8),
          Gender.FEMALE);
      var court = EntityFixtures.court();
      entities.persist(user);
      entities.persist(court);
      entities.flush();
      jdbc.update("update court set thumbnail_image_key = ? where id = ?", key, court.getId());
      return new Fixture(user.getId(), court.getId());
    });

    String token = sessions.login(fixture.userId(), LoginTarget.SERVICE).accessToken();
    var request = HttpRequest.newBuilder(URI.create(
            "http://127.0.0.1:" + port + "/api/v1/courts/" + fixture.courtId() + "/thumbnail"))
        .timeout(Duration.ofSeconds(10))
        .header("Authorization", "Bearer " + token)
        .header("Accept", accept)
        .GET()
        .build();

    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(expectedStatus);
      assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
      assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo(expectedCode);
    }
  }

  private record Fixture(long userId, long courtId) {}
}
