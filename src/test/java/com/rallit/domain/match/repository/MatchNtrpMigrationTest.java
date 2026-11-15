package com.rallit.domain.match.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.rallit.support.IntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class MatchNtrpMigrationTest extends IntegrationTestSupport {

	@Test
	void normalizesLegacyRangesAndPreservesUnsetBounds() {
		try (var postgres = new PostgreSQLContainer(PG.getDockerImageName())) {
			postgres.start();
			var dataSource = new DriverManagerDataSource(
					postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
			Flyway.configure().dataSource(dataSource).target("13").load().migrate();
			var jdbc = new JdbcTemplate(dataSource);
			jdbc.update("""
					WITH host AS (
						INSERT INTO users (role, status, nickname, gender, same_gender_only, created_at, updated_at)
						VALUES ('USER', 'ACTIVE', 'LegacyHost', 'FEMALE', false, now(), now())
						RETURNING id
					), venue AS (
						INSERT INTO court (name, address, latitude, longitude, has_parking, has_shower, created_at, updated_at)
						VALUES ('테스트 코트', '서울 마포구', 37.56, 126.90, false, false, now(), now())
						RETURNING id
					), slots AS (
						INSERT INTO court_slot (court_id, registrant_user_id, court_number, starts_at, ends_at,
							rental_fee, evidence_file_key, status, created_at, updated_at)
						SELECT venue.id, host.id, n::text, localtimestamp + interval '1 day',
							localtimestamp + interval '25 hours', 0, 'test/evidence', 'APPROVED', now(), now()
						FROM venue CROSS JOIN host CROSS JOIN generate_series(1, 5) n
						RETURNING id, registrant_user_id, court_number
					)
					INSERT INTO match (court_slot_id, host_user_id, status, male_recruit_count, female_recruit_count,
						min_ntrp, max_ntrp, participation_fee, recruitment_deadline, created_at, updated_at)
					SELECT slots.id, slots.registrant_user_id, 'RECRUITING', 1, 1,
						ranges.min_ntrp, ranges.max_ntrp, 0, now(), now(), now()
					FROM slots JOIN (VALUES ('1', 4.5, 5.0), ('2', 3.0, 6.0), ('3', NULL, 5.0),
						('4', 5.0, NULL), ('5', 2.5, 3.5)) ranges(number, min_ntrp, max_ntrp)
					ON ranges.number = slots.court_number
					""");

			Flyway.configure().dataSource(dataSource).load().migrate();

			assertThat(jdbc.queryForList("""
					SELECT concat(coalesce(m.min_ntrp::text, 'null'), ':', coalesce(m.max_ntrp::text, 'null'))
					FROM match m JOIN court_slot s ON s.id = m.court_slot_id
					ORDER BY s.court_number
					""", String.class)).containsExactly("4.0:4.0", "3.0:4.0", "null:4.0", "4.0:null", "2.5:3.5");
		}
	}
}
