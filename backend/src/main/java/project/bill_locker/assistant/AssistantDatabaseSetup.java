package project.bill_locker.assistant;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Prepares what the assistant's SQL may read, every time the app starts (safe to repeat).
 * Needs no superuser, so it works the same on your PC and on Neon (see docs/task-8).
 *
 * <ul>
 * <li>A login {@code bill_locker_ai} (password: AI_DATABASE_PASSWORD) that may only SELECT
 * from the views below and belongs to no other role. {@link ReadOnlyQueryRunner} runs the AI's
 * SQL logged in as it, so {@code set_config('role', …)} has no role to switch to.</li>
 * <li>Eight {@code ai_*} views: the user's own data. Secrets (password hash, reset tokens, Gmail
 * tokens) and file bytes are left out. Views read the tables with their owner's rights, so the
 * login never needs rights on a table. They are {@code security_barrier} views: Postgres
 * applies the user filter first. A plain view lets the planner test the AI's own conditions
 * on every row, so an error like "invalid input syntax: 'someone@else.com'" or a 1/0 could
 * reveal other users' values.</li>
 * <li>The user filter needs {@code app.user_id} <b>and</b> {@code app.user_sig}, a signature
 * of that id made with the secret in {@code ai_secret}. The AI's SQL can change the id (it can
 * call set_config), but can't read the secret, so it can't sign another user's id.</li>
 * </ul>
 *
 * Runs after Hibernate has created the tables. No migration files: plain SQL from Java.
 */
@Component
class AssistantDatabaseSetup {

	private static final Logger log = LoggerFactory.getLogger(AssistantDatabaseSetup.class);

	/** The database login the AI's SQL runs as. */
	static final String AI_ROLE = "bill_locker_ai";

	/**
	 * The signed-in user's id, but only when app.user_sig = sha256(secret + id), as
	 * {@link ReadOnlyQueryRunner} sets them for one transaction. Missing or wrong → null → no rows.
	 */
	private static final String ME = """
			(SELECT nullif(current_setting('app.user_id', true), '')::uuid FROM ai_secret s
			  WHERE encode(sha256(convert_to(s.secret || current_setting('app.user_id', true), 'UTF8')), 'hex')
			        = current_setting('app.user_sig', true))""";

	/** View name → its SELECT. Dropped and created again on start, so column changes never fail. */
	private static final List<String[]> VIEWS = List.of(
			new String[] {"ai_service_records", """
                    SELECT r.id, r.document_id, r.item_id AS product_id, i.position AS item_position,
                           i.product_name, r.type, r.title, r.description, r.service_date, r.provider_name,
                           r.provider_contact, r.cost, r.covered_by_warranty, r.claim_number, r.claim_status,
                           r.next_due_date, r.repeat_months, r.attachment_document_id, r.created_at, r.updated_at
                    FROM product_service_records r JOIN document_items i ON i.id=r.item_id
                    JOIN documents d ON d.id=r.document_id
                    WHERE d.status='SAVED' AND d.user_id=r.user_id AND r.user_id = """ + ME},
            new String[] {"ai_me", "SELECT name, email, monthly_budget, created_at FROM users WHERE id = " + ME},
			new String[] {"ai_documents", """
					SELECT id, file_name, content_type, size_bytes, status, document_type, document_number,
					       seller_name, seller_address, seller_contact, buyer_name, buyer_address, buyer_email,
					       purchase_date, tax_amount, total_amount, category, return_by_date, read_error, source_gmail,
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
			new String[] {"ai_category_budgets", """
					SELECT category, amount FROM category_budgets WHERE user_id = """ + ME},
			new String[] {"ai_gmail_files", """
					SELECT f.id, f.email_id, f.file_name, f.content_type, f.size_bytes, f.status, f.document_id, f.error
					FROM gmail_files f JOIN gmail_emails e ON e.id = f.email_id
					WHERE e.user_id = """ + ME});

	/** What Gemini is told about the views. Keep in step with {@link #VIEWS}. */
	public static final String VIEWS_DESCRIPTION = """
            ai_service_records(id, document_id, product_id, item_position, product_name, type, title, description,
              service_date, provider_name, provider_contact, cost, covered_by_warranty, claim_number, claim_status,
              next_due_date, repeat_months, attachment_document_id, created_at, updated_at): repairs and services on
              saved products. type: REPAIR, ROUTINE_SERVICE, PART_REPLACED, WARRANTY_CLAIM, OTHER.
              claim_status: OPEN, RESOLVED, REJECTED or null. cost is actual recorded cost in rupees; null is unknown.
              product_id -> ai_items.id; document_id -> ai_documents.id. For CURRENT reminders, select the newest
              scheduled row per product (service_date desc, created_at desc, id desc), then compare next_due_date
              with today's date. Overdue: before today; due soon: today through today+30 days inclusive.
            ai_me(name, email, monthly_budget, created_at): the signed-in user. monthly_budget: how much they plan
			  to spend per month, in rupees (set on the Expenses page); null = no budget set.
			ai_documents(id, file_name, content_type, size_bytes, status, document_type, document_number,
			  seller_name, seller_address, seller_contact, buyer_name, buyer_address, buyer_email,
			  purchase_date, tax_amount, total_amount, category, return_by_date, read_error, source_gmail, created_at,
			  updated_at):
			  one row per uploaded bill. status: UPLOADED (not read yet), EXTRACTED (read, waiting for review),
			  SAVED (checked by the user). document_type: INVOICE, WARRANTY_CARD, RECEIPT (bills and receipts for
			  rides, food, fuel, utilities, phone...), OTHER. category (what it was spent on, any type): TRAVEL,
			  FOOD, GROCERIES, FUEL, UTILITIES, PHONE_INTERNET, SHOPPING, HEALTH, ELECTRONICS, APPLIANCES, FASHION, HOME,
			  ENTERTAINMENT, OTHER, or null (not sorted yet). Amounts are in rupees.
			  source_gmail: the Gmail address it was imported from, or null when uploaded by hand.
			  return_by_date: the last day the purchase can be returned (null = unknown); "what can I still return"
			  = return_by_date >= CURRENT_DATE.
			ai_items(id, document_id, position, product_name, brand, model_number, serial_number, unit_price,
			  warranty_period_months, warranty_start_date, warranty_end_date, warranty_provider,
			  registration_url, registration_source): one row per product line; document_id -> ai_documents.id.
			ai_gmail_accounts(id, email, scan_status, scan_since, last_scanned_at, last_error, created_at):
			  connected Gmail addresses; scan_status: IDLE, QUEUED, SCANNING, ERROR.
			ai_gmail_emails(id, account_id, from_name, from_email, subject, snippet, received_at, kind):
			  scanned emails; account_id -> ai_gmail_accounts.id; kind: INVOICE, WARRANTY, SERVICE, RECEIPT,
			  ORDER, UNSURE.
			ai_category_budgets(category, amount): the user's monthly spending limit per category (set on the
			  Expenses page), in rupees; a category without a row has no limit.
			ai_gmail_files(id, email_id, file_name, content_type, size_bytes, status, document_id, error):
			  bill files found in emails; status: NEW (to review), IGNORED, IMPORTING, IMPORTED, FAILED;
			  document_id -> ai_documents.id once imported; email_id -> ai_gmail_emails.id.
			""";

	private final JdbcTemplate jdbc;
	private final String aiPassword;

	AssistantDatabaseSetup(JdbcTemplate jdbc, @Value("${app.assistant.db-password:}") String aiPassword) {
		this.jdbc = jdbc;
		this.aiPassword = aiPassword;
	}

	@EventListener(ApplicationReadyEvent.class)
	void setUp() {
		if (aiPassword.isBlank()) {
			log.warn("AI_DATABASE_PASSWORD is not set: the assistant stays off");
			return;
		}
		try {
			jdbc.execute("""
					DO $$ BEGIN
					  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bill_locker_ai') THEN
					    CREATE ROLE bill_locker_ai NOLOGIN;
					  END IF;
					END $$""");
			// A role can't take a password as a "?" parameter, so it goes in as a quoted literal.
			jdbc.execute("ALTER ROLE bill_locker_ai LOGIN PASSWORD '" + aiPassword.replace("'", "''") + "'");
			// The runner sets app.user_id/app.user_sig with set_config. Older versions of this class
			// took set_config away from PUBLIC on your local database; give it back to the AI login.
			// (Elsewhere, e.g. Neon, it never lost it, so nothing to do.)
			if (!Boolean.TRUE.equals(jdbc.queryForObject(
					"SELECT has_function_privilege('bill_locker_ai', 'pg_catalog.set_config(text, text, boolean)', 'EXECUTE')",
					Boolean.class))) {
				jdbc.execute("GRANT EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) TO bill_locker_ai");
			}
			// The secret behind app.user_sig: only our own DB user (and the views) can read it.
			jdbc.execute("CREATE TABLE IF NOT EXISTS ai_secret (secret text NOT NULL)");
			jdbc.execute("REVOKE ALL ON ai_secret FROM PUBLIC");
			jdbc.execute("""
					INSERT INTO ai_secret (secret)
					SELECT gen_random_uuid()::text || gen_random_uuid()::text
					WHERE NOT EXISTS (SELECT 1 FROM ai_secret)""");
			for (String[] view : VIEWS) {
				jdbc.execute("DROP VIEW IF EXISTS " + view[0]);
				jdbc.execute("CREATE VIEW " + view[0] + " WITH (security_barrier = true) AS " + view[1]);
				jdbc.execute("GRANT SELECT ON " + view[0] + " TO bill_locker_ai");
			}
			log.info("Assistant views and the bill_locker_ai login are ready");
		} catch (DataAccessException e) {
			// The rest of the app works without the assistant, so don't stop the start-up.
			log.warn("Assistant database setup failed, the assistant won't work: {}", e.getMostSpecificCause().getMessage());
		}
	}
}
