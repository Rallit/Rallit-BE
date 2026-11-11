package com.rallit.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class IntegrationTestSupport {
  protected static com.rallit.domain.auth.dto.internal.SocialIdentity kakao(String subject) {
    return new com.rallit.domain.auth.dto.internal.SocialIdentity(
        com.rallit.domain.auth.model.OAuthProvider.KAKAO, subject, subject + "@example.test", true);
  }

  static final PostgreSQLContainer PG =
      new PostgreSQLContainer(
          "postgres@sha256:3540c0362c809df084ffac0ae69fe794515f9de92692f02178bcdce0ef0302ac");
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
    r.add("spring.datasource.hikari.maximum-pool-size", () -> 2);
    r.add("spring.datasource.hikari.minimum-idle", () -> 0);
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
