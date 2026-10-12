package project.bill_locker.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs one SELECT written by the AI, for one user, without any way to change data or see
 * other users' rows. The real protection is in the database:
 * a read-only transaction (no writes), the {@code bill_locker_ai} role (only the {@code ai_*}
 * views), and {@code app.user_id} (the views' filter). Our text check only stops obvious
 * nonsense early. See {@link AssistantDatabaseSetup}.
 */
@Component
public class ReadOnlyQueryRunner {

	static final int MAX_ROWS = 50;
	/** Longer values are cut (by Postgres, before they reach us) and end with "…". */
	static final int MAX_VALUE_LENGTH = 500;

	private static final JsonMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS) // amounts stay exact: 8995.00
			.build();

	/**
	 * True when the AI role could escape: it can change settings or take session locks
	 * (e.g. the app's DB user isn't a superuser, so the REVOKEs at startup only warned),
	 * or the role is missing. Then we refuse to run anything.
	 */
	private static final String UNSAFE = """
			SELECT CASE WHEN EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bill_locker_ai') THEN
			    has_function_privilege('bill_locker_ai', 'pg_catalog.set_config(text, text, boolean)', 'EXECUTE')
			    OR has_table_privilege('bill_locker_ai', 'pg_catalog.pg_settings', 'UPDATE')
			    OR has_function_privilege('bill_locker_ai', 'pg_catalog.pg_advisory_lock(bigint)', 'EXECUTE')
			  ELSE true END""";

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transaction;

	ReadOnlyQueryRunner(DataSource dataSource, PlatformTransactionManager transactionManager) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.jdbc.setMaxRows(MAX_ROWS); // a big answer is cut, not loaded whole
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** Rows in column order: numbers, booleans and text (dates and ids as text). */
	public List<Map<String, Object>> run(UUID userId, String sql) {
		String query = checked(sql);
		try {
			return transaction.execute(status -> {
				status.setRollbackOnly(); // nothing to keep: also drops the role and the setting below
				jdbc.execute("SET TRANSACTION READ ONLY"); // must come first in the transaction
				if (Boolean.TRUE.equals(jdbc.queryForObject(UNSAFE, Boolean.class))) {
					throw new AssistantException(AssistantException.FAILED,
							"The assistant is switched off: its database protection isn't in place (see the backend log).");
				}
				jdbc.execute("SET LOCAL statement_timeout = '3s'");
				jdbc.queryForObject("SELECT set_config('app.user_id', ?, true)", String.class, userId.toString());
				jdbc.execute("SET LOCAL ROLE bill_locker_ai"); // from here on: only the ai_* views
				return jdbc.queryForList(cutValues(query), String.class).stream().map(ReadOnlyQueryRunner::row).toList();
			});
		} catch (DataAccessException e) {
			// e.g. "permission denied for table users", "canceling statement due to statement timeout"
			throw new QueryRejectedException(e.getMostSpecificCause().getMessage());
		}
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
