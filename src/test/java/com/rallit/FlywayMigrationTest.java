package com.rallit;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@ActiveProfiles("test")
class FlywayMigrationTest extends IntegrationTestSupport {

	@Autowired
	private Flyway flyway;

	@Test
	void appliesMvpAndAuthenticationMigrations() {
		MigrationInfo current = flyway.info().current();
		assertThat(current.getVersion().getVersion()).isEqualTo("10");
		assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);
		assertThat(flyway.info().pending()).isEmpty();
	}
}
