package project.bill_locker;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tests run against a throwaway PostgreSQL 17 in Docker, never against your local
 * database. (The pgvector image is plain PostgreSQL plus the extension that AI
 * search will need in a later step.)
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres")) {
			@Override
			public void start() {
				super.start();
				// The local DB still contains old-build tables. Reproduce that before Hibernate starts,
				// so tests cannot accidentally pass only because they used a completely empty schema.
				try (var connection = DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword());
						var statement = connection.createStatement()) {
					statement.execute("CREATE TABLE IF NOT EXISTS service_records (id uuid PRIMARY KEY, legacy_note text NOT NULL)");
					statement.execute("""
							INSERT INTO service_records (id, legacy_note)
							VALUES ('00000000-0000-0000-0000-000000000015', 'Old service record retained')
							ON CONFLICT (id) DO NOTHING
							""");
				} catch (SQLException e) {
					throw new IllegalStateException("Could not prepare the legacy-schema test fixture", e);
				}
			}
		};
	}
}
