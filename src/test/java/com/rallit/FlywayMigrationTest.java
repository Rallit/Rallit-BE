package com.rallit;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

	@Autowired
	private Flyway flyway;

	@Test
	void appliesInitialSchemaMigration() {
		MigrationInfo current = flyway.info().current();
		assertThat(current.getVersion().getVersion()).isEqualTo("1");
		assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);
		assertThat(flyway.info().pending()).isEmpty();
	}
}
