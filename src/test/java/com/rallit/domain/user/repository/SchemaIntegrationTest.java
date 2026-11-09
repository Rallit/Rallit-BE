package com.rallit.domain.user.repository;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class SchemaIntegrationTest {
  @Test
  void schemaRejectsDuplicateAccountsAndCaseInsensitiveNicknames() throws Exception {
    try (var pg =
        new PostgreSQLContainer(
            "postgres@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395")) {
      pg.start();
      Flyway.configure()
          .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
          .load()
          .migrate();
      try (var c =
              DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
          var s = c.createStatement()) {
        try (var result = s.executeQuery("select to_regclass('public.user_profiles')")) {
          result.next();
          assertThat(result.getString(1)).isNull();
        }
        s.executeUpdate(
            "insert into"
                + " users(provider,provider_user_id,nickname,role,status,created_at,updated_at)"
                + " values ('KAKAO','1','Rallit','USER','PENDING',now(),now())");
        assertThatThrownBy(
                () ->
                    s.executeUpdate(
                        "insert into"
                            + " users(provider,provider_user_id,nickname,role,status,created_at,updated_at)"
                            + " values ('KAKAO','2','rallit','USER','PENDING',now(),now())"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(
                () ->
                    s.executeUpdate(
                        "insert into"
                            + " users(provider,provider_user_id,nickname,role,status,created_at,updated_at)"
                            + " values ('KAKAO','1','Other','USER','PENDING',now(),now())"))
            .isInstanceOf(SQLException.class);
      }
    }
  }
}
