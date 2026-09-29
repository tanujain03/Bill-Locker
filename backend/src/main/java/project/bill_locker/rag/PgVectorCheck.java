package project.bill_locker.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The document_chunks table needs the pgvector extension. If the database
 * doesn't have it, everything else still works; this logs one clear warning
 * instead of leaving only a Hibernate DDL error in the logs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PgVectorCheck implements ApplicationRunner {

	private final JdbcTemplate jdbc;

	@Override
	public void run(ApplicationArguments args) {
		Boolean installed = jdbc.queryForObject(
				"select exists (select 1 from pg_extension where extname = 'vector')", Boolean.class);
		if (!Boolean.TRUE.equals(installed)) {
			log.warn("pgvector is not installed in this PostgreSQL database, so the document_chunks table "
					+ "(AI document search) was not created. Install pgvector and restart - see docs/database-design.md.");
		}
	}
}
