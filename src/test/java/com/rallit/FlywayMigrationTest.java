package com.rallit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

			assertThat(jdbc.queryForObject("SELECT tennis_started_on FROM users WHERE id = ?", Object.class,
					userId)).isNull();
			assertThat(jdbc.queryForList("SELECT match_type FROM user_preferred_match_type WHERE user_id = ?",
					String.class, userId)).containsExactlyInAnyOrder("DOUBLES", "RALLY");
			assertThat(jdbc.queryForList("""
					SELECT column_name FROM information_schema.columns
					WHERE table_schema = 'public' AND table_name = 'users'
					""", String.class))
					.contains("profile_image_key", "tennis_started_on")
					.doesNotContain("activity_latitude", "activity_longitude", "activity_radius_km",
							"tournament_history", "profile_image_url", "experience_period");
		}
	}

	@Test
	void convertsSeoulWallClockTimestampsToTheSameInstantsRegardlessOfSessionTimeZone() {
		try (var postgres = new PostgreSQLContainer(PG.getDockerImageName())) {
			postgres.start();
			var dataSource = new DriverManagerDataSource(
					postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
			Flyway.configure().dataSource(dataSource).target("20").load().migrate();
			var jdbc = new JdbcTemplate(dataSource);
			Long slotId = jdbc.queryForObject("""
					WITH host AS (
						INSERT INTO users (role, status, nickname, gender, same_gender_only, created_at, updated_at)
						VALUES ('USER', 'ACTIVE', 'SeoulHost', 'FEMALE', false,
							'2026-11-18 00:30:00', '2026-11-18 00:30:00')
						RETURNING id
					), venue AS (
						INSERT INTO court (name, address, latitude, longitude, has_parking, has_shower, created_at, updated_at)
						VALUES ('테스트 코트', '서울 마포구', 37.56, 126.90, false, false,
							'2026-11-18 00:30:00', '2026-11-18 00:30:00')
						RETURNING id
					)
					INSERT INTO court_slot (court_id, registrant_user_id, court_number, starts_at, ends_at,
						rental_fee, evidence_file_key, status, created_at, updated_at)
					SELECT venue.id, host.id, '1', '2026-11-18 14:00:00', '2026-11-18 16:00:00', 0,
						'test/evidence', 'APPROVED', '2026-11-18 00:30:00', '2026-11-18 00:30:00'
					FROM venue CROSS JOIN host
					RETURNING id
					""", Long.class);

			Flyway.configure().dataSource(dataSource).initSql("SET TIME ZONE 'America/Los_Angeles'").load().migrate();

			assertThat(jdbc.queryForObject("""
					SELECT starts_at = timestamptz '2026-11-18T05:00:00Z'
						AND ends_at = timestamptz '2026-11-18T07:00:00Z'
						AND created_at = timestamptz '2026-11-17T15:30:00Z'
					FROM court_slot WHERE id = ?
					""", Boolean.class, slotId)).isTrue();
			assertThat(jdbc.queryForList("""
					SELECT table_name || '.' || column_name FROM information_schema.columns
					WHERE table_schema = 'public' AND data_type = 'timestamp without time zone'
					AND table_name <> 'flyway_schema_history'
					""", String.class)).isEmpty();
			assertThatThrownBy(() -> jdbc.update("""
					INSERT INTO court_slot (court_id, registrant_user_id, court_number, starts_at, ends_at,
						rental_fee, evidence_file_key, status, created_at, updated_at)
					SELECT court_id, registrant_user_id, court_number, timestamptz '2026-11-18T06:00:00Z',
						timestamptz '2026-11-18T08:00:00Z', 0, 'test/evidence', 'APPROVED', now(), now()
					FROM court_slot WHERE id = ?
					""", slotId))
					.isInstanceOf(DataIntegrityViolationException.class)
					.hasMessageContaining("ex_court_slot_overlap");
		}
	}
}
