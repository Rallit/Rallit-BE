package com.rallit.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class IntegrationTestSupport {
  static final PostgreSQLContainer PG =
      new PostgreSQLContainer(
          "postgres@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395");
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(
              "redis@sha256:71885d8cc6e42f2bc0aa2eb853bfc4be200b5108a46ac50e117ae1df0531fb80")
          .withExposedPorts(6379);

  static {
    PG.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", PG::getJdbcUrl);
    r.add("spring.datasource.username", PG::getUsername);
    r.add("spring.datasource.password", PG::getPassword);
    r.add("spring.data.redis.host", REDIS::getHost);
    r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    r.add(
        "rallit.auth.service-key",
        () ->
            java.util.Base64.getEncoder()
                .encodeToString("service-test-key-32-characters-long!!!".getBytes()));
    r.add(
        "rallit.auth.admin-key",
        () ->
            java.util.Base64.getEncoder()
                .encodeToString("admin-test-key-32-characters-long!!!!!".getBytes()));
    r.add("rallit.auth.frontend-callback-url", () -> "https://rallit.co.kr/auth/callback");
    r.add(
        "rallit.auth.admin-frontend-callback-url",
        () -> "https://admin.rallit.co.kr/auth/callback");
    r.add("rallit.auth.kakao-client-id", () -> "test-client");
    r.add(
        "rallit.auth.kakao-redirect-uri",
        () -> "https://api.rallit.co.kr/api/v1/auth/kakao/callback");
  }
}
