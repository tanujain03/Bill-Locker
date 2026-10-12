package project.bill_locker.assistant;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Prepares what the assistant's SQL may read, every time the app starts (safe to repeat).
 *
 * <ul>
 * <li>Six {@code ai_*} views: the user's own data, filtered by the {@code app.user_id}
 * setting that {@link ReadOnlyQueryRunner} sets from the login token. No setting → no rows.
 * Secrets (password hash, reset tokens, Gmail tokens) and file bytes are left out.
 * They are {@code security_barrier} views: Postgres applies the user filter first. A plain
 * view lets the planner test the AI's own conditions on every row, so an error like
 * "invalid input syntax: 'someone@else.com'" or a 1/0 could reveal other users' values.</li>
 * <li>A role {@code bill_locker_ai} that may only SELECT from those views. Views read the
 * tables with their owner's rights, so the role never needs rights on a table.</li>
 * <li>{@code set_config} is taken away from everyone but our own DB user: otherwise the AI's
 * SQL could change {@code app.user_id} and read another user's rows.</li>
 * <li>Session advisory locks are taken away too: they outlive the rollback, and millions of
 * them would fill Postgres's lock table for every database on the server.</li>
 * </ul>
 *
 * Runs after Hibernate has created the tables. No migration files: plain SQL from Java.
 */
@Component
class AssistantDatabaseSetup {

	private static final Logger log = LoggerFactory.getLogger(AssistantDatabaseSetup.class);

	/**
	 * The signed-in user's id, as {@link ReadOnlyQueryRunner} sets it for one transaction.
	 * Outside that it is missing, or '' on a connection that used it before: both mean no user.
	 */
	private static final String ME = "nullif(current_setting('app.user_id', true), '')::uuid";

	/** View name → its SELECT. Dropped and created again on start, so column changes never fail. */
	private static final List<String[]> VIEWS = List.of(
			new String[] {"ai_me", "SELECT name, email, created_at FROM users WHERE id = " + ME},
			new String[] {"ai_documents", """
					SELECT id, file_name, content_type, size_bytes, status, document_type, document_number,
					       seller_name, seller_address, seller_contact, buyer_name, buyer_address, buyer_email,
					       purchase_date, tax_amount, total_amount, category, read_error, source_gmail,
					       created_at, updated_at
					FROM documents WHERE user_id = """ + ME},
			new String[] {"ai_items", """
					SELECT i.id, i.document_id, i.position, i.product_name, i.brand, i.model_number, i.serial_number,
					       i.unit_price, i.warranty_period_months, i.warranty_start_date, i.warranty_end_date,
					       i.warranty_provider, i.registration_url, i.registration_source
					FROM document_items i JOIN documents d ON d.id = i.document_id
					WHERE d.user_id = """ + ME},
			new String[] {"ai_gmail_accounts", """
					SELECT id, email, scan_status, scan_since, last_scanned_at, last_error, created_at
					FROM gmail_accounts WHERE user_id = """ + ME},
			new String[] {"ai_gmail_emails", """
					SELECT id, account_id, from_name, from_email, subject, snippet, received_at, kind
					FROM gmail_emails WHERE user_id = """ + ME},
			new String[] {"ai_gmail_files", """
					SELECT f.id, f.email_id, f.file_name, f.content_type, f.size_bytes, f.status, f.document_id, f.error
					FROM gmail_files f JOIN gmail_emails e ON e.id = f.email_id
					WHERE e.user_id = """ + ME});

	/** Advisory locks that last for the whole session (the transaction-level ones end with the rollback). */
	static final List<String> SESSION_LOCKS = List.of(
			"pg_advisory_lock(bigint)", "pg_advisory_lock(integer, integer)",
			"pg_advisory_lock_shared(bigint)", "pg_advisory_lock_shared(integer, integer)",
			"pg_try_advisory_lock(bigint)", "pg_try_advisory_lock(integer, integer)",
			"pg_try_advisory_lock_shared(bigint)", "pg_try_advisory_lock_shared(integer, integer)");

	/** What Gemini is told about the views. Keep in step with {@link #VIEWS}. */
	public static final String VIEWS_DESCRIPTION = """
			ai_me(name, email, created_at): the signed-in user.
			ai_documents(id, file_name, content_type, size_bytes, status, document_type, document_number,
			  seller_name, seller_address, seller_contact, buyer_name, buyer_address, buyer_email,
			  purchase_date, tax_amount, total_amount, category, read_error, source_gmail, created_at, updated_at):
			  one row per uploaded bill. status: UPLOADED (not read yet), EXTRACTED (read, waiting for review),
			  SAVED (checked by the user). document_type: INVOICE, WARRANTY_CARD, RECEIPT (bills and receipts for
			  rides, food, fuel, utilities, phone...), OTHER. category (receipts only): TRAVEL, FOOD, GROCERIES,
			  FUEL, UTILITIES, PHONE_INTERNET, SHOPPING, HEALTH, OTHER. Amounts are in rupees.
			  source_gmail: the Gmail address it was imported from, or null when uploaded by hand.
			ai_items(id, document_id, position, product_name, brand, model_number, serial_number, unit_price,
			  warranty_period_months, warranty_start_date, warranty_end_date, warranty_provider,
			  registration_url, registration_source): one row per product line; document_id -> ai_documents.id.
			ai_gmail_accounts(id, email, scan_status, scan_since, last_scanned_at, last_error, created_at):
			  connected Gmail addresses; scan_status: IDLE, QUEUED, SCANNING, ERROR.
			ai_gmail_emails(id, account_id, from_name, from_email, subject, snippet, received_at, kind):
			  scanned emails; account_id -> ai_gmail_accounts.id; kind: INVOICE, WARRANTY, SERVICE, RECEIPT,
			  ORDER, UNSURE.
			ai_gmail_files(id, email_id, file_name, content_type, size_bytes, status, document_id, error):
			  bill files found in emails; status: NEW (to review), IGNORED, IMPORTING, IMPORTED, FAILED;
			  document_id -> ai_documents.id once imported; email_id -> ai_gmail_emails.id.
			""";

	private final JdbcTemplate jdbc;

	AssistantDatabaseSetup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@EventListener(ApplicationReadyEvent.class)
	void setUp() {
		try {
			jdbc.execute("""
					DO $$ BEGIN
					  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bill_locker_ai') THEN
					    CREATE ROLE bill_locker_ai NOLOGIN;
					  END IF;
					END $$""");
			// Our own DB user must be allowed to "SET ROLE bill_locker_ai".
			jdbc.execute("GRANT bill_locker_ai TO CURRENT_USER");
			// Only our own DB user may change settings; the AI role may not (see the class comment).
			jdbc.execute("REVOKE EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) FROM PUBLIC");
			jdbc.execute("GRANT EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) TO CURRENT_USER");
			jdbc.execute("REVOKE UPDATE ON pg_catalog.pg_settings FROM PUBLIC");
			for (String lock : SESSION_LOCKS) {
				jdbc.execute("REVOKE EXECUTE ON FUNCTION pg_catalog." + lock + " FROM PUBLIC");
				jdbc.execute("GRANT EXECUTE ON FUNCTION pg_catalog." + lock + " TO CURRENT_USER");
			}
			for (String[] view : VIEWS) {
				jdbc.execute("DROP VIEW IF EXISTS " + view[0]);
				jdbc.execute("CREATE VIEW " + view[0] + " WITH (security_barrier = true) AS " + view[1]);
				jdbc.execute("GRANT SELECT ON " + view[0] + " TO bill_locker_ai");
			}
			log.info("Assistant views and the bill_locker_ai role are ready");
		} catch (DataAccessException e) {
			// The rest of the app works without the assistant, so don't stop the start-up.
			log.warn("Assistant database setup failed, the assistant won't work: {}", e.getMostSpecificCause().getMessage());
		}
	}
}
