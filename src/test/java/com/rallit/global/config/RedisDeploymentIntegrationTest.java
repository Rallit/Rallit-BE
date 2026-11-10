package com.rallit.global.config;

import static org.assertj.core.api.Assertions.*;

import com.rallit.support.DeploymentTestSupport;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.ObjectMapper;

class RedisDeploymentIntegrationTest extends DeploymentTestSupport {
  private final String project = "rallit-redis-it-" + UUID.randomUUID().toString().substring(0, 8);
  private final String password = "isolated-test-" + UUID.randomUUID();
  private Path environmentFile;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void fixture() throws Exception {
    var values = exampleEnvironment();
    values.put("REDIS_PASSWORD", password);
    values.put("REDIS_MEM_LIMIT", "128m");
    values.put("REDIS_MAXMEMORY", "32mb");
    values.put("JWT_SERVICE_KEY", "test-service-placeholder");
    values.put("JWT_ADMIN_KEY", "test-admin-placeholder");
    values.put("KAKAO_REST_API_KEY", "test-kakao-client");
    values.put("KAKAO_REDIRECT_URI", "https://api.example.test/api/v1/auth/kakao/callback");
    values.put("FRONTEND_CALLBACK_URL", "https://frontend.example.test/auth/callback");
    values.put("ADMIN_FRONTEND_CALLBACK_URL", "https://admin.example.test/auth/callback");
    values.put("SERVICE_ORIGINS", "https://frontend.example.test");
    values.put("ADMIN_ORIGINS", "https://admin.example.test");
    environmentFile = temporary.resolve("compose.env");
    Files.write(
        environmentFile,
        values.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
  }

  private Result compose(String profiles, String... arguments) throws Exception {
    var command =
        new ArrayList<>(
            List.of(
                "docker",
                "compose",
                "--env-file",
                environmentFile.toString(),
                "--project-name",
                project,
                "-f",
                repository.resolve("compose.yml").toString()));
    command.addAll(List.of(arguments));
    return run(Map.of("COMPOSE_PROFILES", profiles), command.toArray(String[]::new));
  }

  @Test
  void configurationKeepsRedisPrivateAndPassesAuthEnvironmentWithOrWithoutDatabase()
      throws Exception {
    for (String profiles : List.of("", "local-db")) {
      var result = compose(profiles, "config", "--format", "json");
      assertThat(result.exitCode()).as(result.output()).isZero();
      var services = json.readTree(result.output()).path("services");
      assertThat(services.has("redis")).isTrue();
      assertThat(services.has("db")).isEqualTo(!profiles.isEmpty());
      var redis = services.path("redis");
      assertThat(redis.path("ports").isMissingNode() || redis.path("ports").isEmpty()).isTrue();
      var app = services.path("app").path("environment");
      assertThat(app.path("REDIS_HOST").asText()).isEqualTo("redis");
      assertThat(app.path("REDIS_PASSWORD").asText()).isEqualTo(password);
      assertThat(app.path("JWT_SERVICE_KEY").asText()).isEqualTo("test-service-placeholder");
      assertThat(app.path("KAKAO_REST_API_KEY").asText()).isEqualTo("test-kakao-client");
      assertThat(app.path("FRONTEND_CALLBACK_URL").asText())
          .isEqualTo("https://frontend.example.test/auth/callback");
      assertThat(app.path("SERVICE_ORIGINS").asText()).isEqualTo("https://frontend.example.test");
    }
  }

  private String authenticated(String container, String... args) throws Exception {
    var command =
        new ArrayList<>(
            List.of(
                "docker",
                "exec",
                "--env",
                "REDISCLI_AUTH=" + password,
                container,
                "redis-cli",
                "--raw"));
    command.addAll(List.of(args));
    var result = run(Map.of(), command.toArray(String[]::new));
    assertThat(result.exitCode()).as(result.output()).isZero();
    return result.output().strip();
  }

  @Test
  @Timeout(180)
  void redisRequiresAuthenticationAndPreservesDataAcrossContainerReplacement() throws Exception {
    try {
      var started = compose("", "up", "-d", "--wait", "--wait-timeout", "60", "redis");
      assertThat(started.exitCode()).as(started.output()).isZero();
      String container = compose("", "ps", "-q", "redis").output().strip();
      assertThat(authenticated(container, "PING")).isEqualTo("PONG");
      assertThat(run(Map.of(), "docker", "exec", container, "redis-cli", "--raw", "PING").output())
          .contains("NOAUTH");
      var inspect = run(Map.of(), "docker", "inspect", container);
      var runtime = json.readTree(inspect.output()).get(0);
      assertThat(
              runtime.path("HostConfig").path("PortBindings").isNull()
                  || runtime.path("HostConfig").path("PortBindings").isEmpty())
          .isTrue();
      assertThat(runtime.path("Config").path("User").asText()).isEqualTo("redis");
      assertThat(authenticated(container, "SET", "auth:deployment-test", "retained", "EX", "120"))
          .isEqualTo("OK");
      var config = run(Map.of(), "docker", "exec", container, "cat", "/tmp/rallit-redis.conf");
      assertThat(config.output()).doesNotContain(password);
      assertThat(authenticated(container, "CONFIG", "GET", "maxmemory-policy"))
          .contains("noeviction");
      var recreated =
          compose("", "up", "-d", "--force-recreate", "--wait", "--wait-timeout", "60", "redis");
      assertThat(recreated.exitCode()).as(recreated.output()).isZero();
      String next = compose("", "ps", "-q", "redis").output().strip();
      assertThat(next).isNotEqualTo(container);
      assertThat(authenticated(next, "GET", "auth:deployment-test")).isEqualTo("retained");
    } finally {
      var cleanup = compose("", "down", "--volumes", "--remove-orphans", "--timeout", "10");
      assertThat(cleanup.exitCode()).as(cleanup.output()).isZero();
    }
  }
}
