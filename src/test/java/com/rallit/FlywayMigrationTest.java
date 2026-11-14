package com.rallit;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@ActiveProfiles("test")
class FlywayMigrationTest extends IntegrationTestSupport {

	@Autowired
	private Flyway flyway;

	@Test
	void appliesMvpAndAuthenticationMigrations() {
		MigrationInfo current = flyway.info().current();
		assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);
		assertThat(flyway.info().pending()).isEmpty();
	}

	@Test
	void upgradesExistingV3DatabaseAndKeepsLegacyMemberPreferences() {
		try (var postgres = new PostgreSQLContainer(PG.getDockerImageName())) {
			postgres.start();
			var dataSource = new DriverManagerDataSource(
					postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
			Flyway.configure().dataSource(dataSource).target("3").load().migrate();
			var jdbc = new JdbcTemplate(dataSource);
			Long userId = jdbc.queryForObject("""
					INSERT INTO users (role, status, nickname, gender, experience_period,
						same_gender_only, created_at, updated_at)
					VALUES ('USER', 'ACTIVE', 'LegacyMember', 'FEMALE', 'TWO_YEARS', false, now(), now())
					RETURNING id
					""", Long.class);
			jdbc.update("""
					INSERT INTO user_preferred_match_type (user_id, match_type)
					VALUES (?, 'WOMENS_DOUBLES'), (?, 'MENS_DOUBLES'), (?, 'RALLY')
					""", userId, userId, userId);

			Flyway.configure().dataSource(dataSource).load().migrate();

			assertThat(jdbc.queryForObject("SELECT experience_period FROM users WHERE id = ?", String.class,
					userId)).isEqualTo("ONE_TO_THREE_YEARS");
			assertThat(jdbc.queryForList("SELECT match_type FROM user_preferred_match_type WHERE user_id = ?",
					String.class, userId)).containsExactlyInAnyOrder("DOUBLES", "RALLY");
			assertThat(jdbc.queryForList("""
					SELECT column_name FROM information_schema.columns
					WHERE table_schema = 'public' AND table_name = 'users'
					""", String.class))
					.contains("profile_image_key")
					.doesNotContain("activity_latitude", "activity_longitude", "activity_radius_km",
							"tournament_history", "profile_image_url");
		}
	}
}
