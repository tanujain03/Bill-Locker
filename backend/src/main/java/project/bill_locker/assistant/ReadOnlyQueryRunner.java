package project.bill_locker.assistant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs one SELECT written by the AI, for one user, without any way to change data or see
 * other users' rows. The real protection is in the database, and needs no superuser
 * (docs/task-8-assistant-on-neon-plan.md):
 * <ul>
 * <li>a <b>new connection logged in as {@code bill_locker_ai}</b>: rights on the {@code ai_*}
 * views only, member of no role, so the AI's SQL has no role to switch to. It is closed after
 * the question, which drops anything the SQL left behind (settings, session locks);</li>
 * <li>a read-only transaction (no writes);</li>
 * <li>{@code app.user_id} + {@code app.user_sig} (the views' filter): the AI's SQL can change
 * the id but can't sign it;</li>
 * <li>a time limit enforced by Java (cancels the query), which the SQL can't switch off.</li>
 * </ul>
 * Questions run one at a time: then no other user's chat connection is open that the AI's SQL
 * could look at (Postgres shows a login its own other sessions in pg_stat_activity).
 * Our text check only stops obvious nonsense early. See {@link AssistantDatabaseSetup}.
 */
@Component
public class ReadOnlyQueryRunner {

	private static final Logger log = LoggerFactory.getLogger(ReadOnlyQueryRunner.class);

	static final int MAX_ROWS = 50;
	/** Longer values are cut (by Postgres, before they reach us) and end with "…". */
	static final int MAX_VALUE_LENGTH = 500;
	static final int TIMEOUT_SECONDS = 3;

	private static final JsonMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS) // amounts stay exact: 8995.00
			.build();

	/** True only when we really are the AI login, not a superuser, and a member of no role. */
	private static final String SAFE = """
			SELECT current_user = 'bill_locker_ai' AND NOT r.rolsuper
			       AND NOT EXISTS (SELECT 1 FROM pg_auth_members m WHERE m.member = r.oid)
			FROM pg_roles r WHERE r.rolname = current_user""";

	private final JdbcTemplate app; // our own login: only reads the secret
	private final String url;
	private final String aiPassword;
	private final ReentrantLock oneAtATime = new ReentrantLock();

	ReadOnlyQueryRunner(DataSource dataSource, JdbcConnectionDetails database,
			@Value("${app.assistant.db-password:}") String aiPassword) {
		this.app = new JdbcTemplate(dataSource);
		this.url = database.getJdbcUrl(); // same database as the app, other login
		this.aiPassword = aiPassword;
	}

	/** Rows in column order: numbers, booleans and text (dates and ids as text). */
	public List<Map<String, Object>> run(UUID userId, String sql) {
		String query = checked(sql);
		String signature = sign(userId);
		lockOrBusy();
		try (Connection connection = DriverManager.getConnection(url, AssistantDatabaseSetup.AI_ROLE, aiPassword)) {
			// Closing the connection ends the transaction without keeping anything.
			connection.setAutoCommit(false);
			JdbcTemplate ai = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
			ai.setMaxRows(MAX_ROWS); // a big answer is cut, not loaded whole
			ai.setQueryTimeout(TIMEOUT_SECONDS); // Java cancels the query: the SQL can't turn this off
			ai.execute("SET TRANSACTION READ ONLY"); // must come first in the transaction
			if (!Boolean.TRUE.equals(ai.queryForObject(SAFE, Boolean.class))) {
				throw switchedOff();
			}
			ai.execute("SET LOCAL statement_timeout = '" + TIMEOUT_SECONDS + "s'"); // a second guard, inside Postgres
			// "?" parameters: the values never appear in the statement text Postgres shows.
			ai.queryForList("SELECT set_config('app.user_id', ?, true), set_config('app.user_sig', ?, true)",
					userId.toString(), signature);
			return ai.queryForList(cutValues(query), String.class).stream().map(ReadOnlyQueryRunner::row).toList();
		} catch (DataAccessException e) {
			// e.g. "permission denied for table users", "canceling statement due to user request" (timeout)
			throw new QueryRejectedException(e.getMostSpecificCause().getMessage());
		} catch (SQLException e) {
			// The login itself failed: wrong AI_DATABASE_PASSWORD, or the database is unreachable.
			log.warn("The assistant could not log in as {}: {}", AssistantDatabaseSetup.AI_ROLE, e.getMessage());
			throw switchedOff();
		} finally {
			oneAtATime.unlock();
		}
	}

	/** sha256(secret + id) in hex: the same sum the views compute (see AssistantDatabaseSetup.ME). */
	private String sign(UUID userId) {
		if (aiPassword.isBlank()) {
			throw switchedOff();
		}
		String secret;
		try {
			secret = app.queryForObject("SELECT secret FROM ai_secret", String.class);
		} catch (DataAccessException e) {
			log.warn("The assistant has no secret yet (did the startup setup fail?): {}", e.getMostSpecificCause().getMessage());
			throw switchedOff();
		}
		try {
			byte[] sum = MessageDigest.getInstance("SHA-256").digest((secret + userId).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(sum);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // every Java has SHA-256
		}
	}

	private void lockOrBusy() {
		try {
			if (oneAtATime.tryLock(10, TimeUnit.SECONDS)) {
				return;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		throw new AssistantException(AssistantException.FAILED, "The assistant is busy. Please try again in a moment.");
	}

	private static AssistantException switchedOff() {
		return new AssistantException(AssistantException.FAILED,
				"The assistant is switched off: its database protection isn't in place (see the backend log).");
	}

	/**
	 * Wraps the AI's query so Postgres returns each row as one JSON object with every value cut
	 * to MAX_VALUE_LENGTH: a huge value (repeat('x', 300000000)) never reaches the backend.
	 * The new line before ")" keeps a trailing "-- comment" in the AI's query from hiding it.
	 */
	private static String cutValues(String query) {
		return """
				SELECT (SELECT json_object_agg(key, CASE
				          WHEN length(value::text) <= %1$d THEN value
				          WHEN json_typeof(value) = 'string' THEN to_json(left(value #>> '{}', %1$d) || '…')
				          ELSE to_json(left(value::text, %1$d) || '…') END)
				        FROM json_each(row_to_json(q)))::text
				FROM (
				""".formatted(MAX_VALUE_LENGTH)
				+ query // not passed through formatted(): the AI's SQL may contain "%"
				+ "\n) q LIMIT " + MAX_ROWS;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> row(String json) {
		return json == null ? Map.of() : JSON.readValue(json, LinkedHashMap.class); // LinkedHashMap keeps column order
	}

	/** One SELECT (or WITH … SELECT), nothing else. */
	private static String checked(String sql) {
		String query = sql == null ? "" : sql.strip();
		if (query.endsWith(";")) {
			query = query.substring(0, query.length() - 1).strip(); // AIs like to end with ";"
		}
		String lower = query.toLowerCase(Locale.ROOT);
		if (!(lower.startsWith("select") || lower.startsWith("with"))) {
			throw new QueryRejectedException("Only a SELECT query is allowed.");
		}
		if (query.contains(";")) {
			throw new QueryRejectedException("Only one statement is allowed.");
		}
		if (lower.contains("set_config") || lower.contains("pg_settings")) {
			throw new QueryRejectedException("Settings can't be read or changed.");
		}
		return query;
	}

}
