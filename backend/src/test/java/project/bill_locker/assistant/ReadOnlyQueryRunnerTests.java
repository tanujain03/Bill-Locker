package project.bill_locker.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import project.bill_locker.document.DocumentApiTestBase;

/**
 * The assistant's SQL comes from an AI, so these tests play the attacker: every
 * query must stay read-only, inside the user's own rows, and away from secrets.
 */
class ReadOnlyQueryRunnerTests extends DocumentApiTestBase {

	@Autowired
	ReadOnlyQueryRunner runner;

	@Autowired
	JdbcTemplate jdbc;

	String emailA;
	UUID userA;
	UUID userB;

	@BeforeEach
	void twoUsersWithOneBillEach() throws Exception {
		emailA = uniqueEmail("a");
		String emailB = uniqueEmail("b");
		saveBill(registerAndGetToken(emailA), bill("Croma", "1499"));
		saveBill(registerAndGetToken(emailB), bill("Reliance", "999"));
		userA = userId(emailA);
		userB = userId(emailB);
	}

	@Test
	void onlyOwnRows() {
		List<Map<String, Object>> rows = runner.run(userA, "SELECT seller_name FROM ai_documents");
		assertThat(rows).extracting(r -> r.get("seller_name")).containsExactly("Croma");
	}

	@Test
	void noUserSettingMeansNoRows() {
		assertThat(jdbc.queryForList("SELECT * FROM ai_documents")).isEmpty();
	}

	@Test
	void deleteIsRejectedByTextCheck() {
		assertThatThrownBy(() -> runner.run(userA, "DELETE FROM documents")).isInstanceOf(QueryRejectedException.class);
		assertThat(runner.run(userA, "SELECT id FROM ai_documents")).hasSize(1);
	}

	@Test
	void writeInsideWithIsRejectedByDatabase() {
		assertThatThrownBy(() -> runner.run(userA, "WITH x AS (DELETE FROM document_items RETURNING id) SELECT * FROM x"))
				.isInstanceOf(QueryRejectedException.class);
		assertThat(runner.run(userA, "SELECT document_id FROM ai_items")).hasSize(1);
	}

	@Test
	void realTablesAreOffLimits() {
		assertThatThrownBy(() -> runner.run(userA, "SELECT password_hash FROM users")).isInstanceOf(QueryRejectedException.class);
		assertThatThrownBy(() -> runner.run(userA, "SELECT * FROM documents")).isInstanceOf(QueryRejectedException.class);
	}

	@Test
	void twoStatementsAreRejected() {
		assertThatThrownBy(() -> runner.run(userA, "SELECT 1; DROP TABLE documents")).isInstanceOf(QueryRejectedException.class);
	}

	@Test
	void trailingSemicolonIsAllowed() {
		assertThat(runner.run(userA, "SELECT count(*) AS n FROM ai_documents;  ")).containsExactly(Map.of("n", 1));
	}

	@Test
	void setConfigTrickIsRejected() {
		String sql = "WITH x AS (SELECT set_config('app.user_id', '" + userB + "', true)) SELECT seller_name FROM x, ai_documents";
		assertThatThrownBy(() -> runner.run(userA, sql)).isInstanceOf(QueryRejectedException.class);
	}

	@Test
	void setConfigIsRevokedForRole() {
		// Checks the database itself, past the text check: the AI role can't call set_config at all.
		assertThat(jdbc.queryForObject(
				"SELECT has_function_privilege('bill_locker_ai', 'pg_catalog.set_config(text,text,boolean)', 'EXECUTE')",
				Boolean.class)).isFalse();
	}

	@Test
	void slowQueryIsCancelled() {
		long start = System.nanoTime();
		assertThatThrownBy(() -> runner.run(userA, "SELECT pg_sleep(10)")).isInstanceOf(QueryRejectedException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(6));
	}

	@Test
	void atMostFiftyRows() {
		assertThat(runner.run(userA, "SELECT g FROM generate_series(1, 100) g")).hasSize(50);
	}

	@Test
	void connectionIsCleanAfterRun() {
		for (int i = 0; i < 5; i++) {
			runner.run(userA, "SELECT 1 AS one");
		}
		assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isNotEqualTo("bill_locker_ai");
		assertThat(jdbc.queryForObject("SELECT coalesce(current_setting('app.user_id', true), '')", String.class)).isEmpty();
	}

	@Test
	void everyViewWorksAndShowsOwnRows() {
		for (String view : List.of("ai_me", "ai_documents", "ai_items", "ai_gmail_accounts", "ai_gmail_emails", "ai_gmail_files")) {
			assertThatCode(() -> runner.run(userA, "SELECT * FROM " + view)).doesNotThrowAnyException();
		}
		assertThat(runner.run(userA, "SELECT email FROM ai_me")).extracting(r -> r.get("email")).containsExactly(emailA);
		assertThat(runner.run(userA, "SELECT product_name FROM ai_items")).extracting(r -> r.get("product_name"))
				.containsExactly("Thing");
	}

	@Test
	void errorsNeverQuoteOtherUsersValues() {
		// A plain view lets the planner test the AI's condition before the user filter, so the
		// cast error would quote B's seller ("Reliance"). security_barrier views filter first.
		assertThatThrownBy(() -> runner.run(userA, "SELECT 1 FROM ai_documents WHERE seller_name::int = 0"))
				.isInstanceOf(QueryRejectedException.class)
				.message().doesNotContain("Reliance").contains("Croma");
		assertThatThrownBy(() -> runner.run(userA, "SELECT 1 FROM ai_me WHERE email::int = 0"))
				.message().contains(emailA); // the only row the view may test is A's own
	}

	@Test
	void everyViewIsASecurityBarrier() {
		// Whether a plain view leaks depends on the query plan (the tests above can pass by luck),
		// so check the setting itself: it makes Postgres apply the user filter first, always.
		List<String> plain = jdbc.queryForList("""
				SELECT relname FROM pg_class
				WHERE relkind = 'v' AND relname LIKE 'ai\\_%' AND NOT coalesce('security_barrier=true' = ANY (reloptions), false)
				""", String.class);
		assertThat(plain).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_class WHERE relkind = 'v' AND relname LIKE 'ai\\_%'", Long.class))
				.isEqualTo(6);
	}

	@Test
	void yesNoQuestionsAboutOtherUsersSeeNothing() {
		String sql = "SELECT count(*) AS n FROM ai_documents WHERE CASE WHEN seller_name = 'Reliance' THEN 1 / (length(seller_name) - length(seller_name)) ELSE 1 END = 1";
		assertThat(runner.run(userA, sql)).containsExactly(Map.of("n", 1));
	}

	@Test
	void advisoryLocksAreOffLimits() {
		// Session locks outlive the rollback: millions of them would fill Postgres's lock table.
		assertThatThrownBy(() -> runner.run(userA, "SELECT pg_advisory_lock(1)")).isInstanceOf(QueryRejectedException.class);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'", Long.class)).isZero();
	}

	@Test
	void hugeValuesAreCut() {
		List<Map<String, Object>> rows = runner.run(userA, "SELECT repeat('x', 50000000) AS x");
		assertThat((String) rows.getFirst().get("x")).hasSizeLessThanOrEqualTo(ReadOnlyQueryRunner.MAX_VALUE_LENGTH + 1);
	}

	@Test
	void failsClosedWhenTheRoleCanChangeSettings() {
		// e.g. the app's DB user isn't a superuser, so the REVOKE at startup only warned.
		jdbc.execute("GRANT EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) TO bill_locker_ai");
		try {
			assertThatThrownBy(() -> runner.run(userA, "SELECT 1 AS one")).isInstanceOf(AssistantException.class);
		} finally {
			jdbc.execute("REVOKE EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) FROM bill_locker_ai");
		}
	}

	private UUID userId(String email) {
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
	}

	private static String bill(String seller, String total) {
		return """
				{"sellerName": "%s", "purchaseDate": "2026-10-01", "totalAmount": %s, "items": [{"productName": "Thing"}]}
				""".formatted(seller, total);
	}
}
