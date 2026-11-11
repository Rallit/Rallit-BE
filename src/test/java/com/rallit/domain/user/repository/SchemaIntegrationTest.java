package com.rallit.domain.user.repository;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class SchemaIntegrationTest {
  @Test
  void upgradesMvpSchemaAndRejectsDuplicateAccountsAndCaseInsensitiveNicknames() throws Exception {
    try (var pg =
        new PostgreSQLContainer(
            "postgres@sha256:3540c0362c809df084ffac0ae69fe794515f9de92692f02178bcdce0ef0302ac")) {
      pg.start();
      var config = Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
      config.target("1").load().migrate();
      try (var connection =
              DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
          var statement = connection.createStatement()) {
        statement.executeUpdate("""
            insert into users(id,nickname,role,status,same_gender_only,created_at,updated_at)
            values (1,'Rallit','USER','ONBOARDING',false,now(),now()),
                   (2,'Other','USER','ONBOARDING',false,now(),now())
            """);
        statement.executeUpdate("""
            insert into social_account(user_id,provider,provider_user_id,created_at,updated_at)
            values (1,'KAKAO','1',now(),now())
            """);

        config.target("latest").load().migrate();

        try (var result = statement.executeQuery("""
            select users.nickname, social_account.provider_user_id
            from users join social_account on social_account.user_id = users.id
            """)) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString("nickname")).isEqualTo("Rallit");
          assertThat(result.getString("provider_user_id")).isEqualTo("1");
        }
        assertThatThrownBy(
                () -> statement.executeUpdate("update users set nickname='rallit' where id=2"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_users_nickname");
        assertThatThrownBy(
                () -> statement.executeUpdate("""
                    insert into social_account(user_id,provider,provider_user_id,created_at,updated_at)
                    values (2,'KAKAO','1',now(),now())
                    """))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_social_account_provider_provider_user_id");
      }
    }
  }
}
