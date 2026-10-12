package project.bill_locker.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import project.bill_locker.gmail.GmailService.ScanJob;
import project.bill_locker.gmail.GoogleApi.Attachment;
import project.bill_locker.gmail.GoogleApi.Email;
import project.bill_locker.gmail.GoogleApi.GoogleApiException;

class GmailScanTests extends GmailApiTestBase {

	private static final String GMAIL = "/api/integrations/gmail";
	private static final long BIG = 50 * 1024;

	@Autowired
	GmailScanWorker scanWorker;

	@Autowired
	GmailService gmailService;

	@Autowired
	GmailAccountRepository accounts;

	@Autowired
	GmailEmailRepository emails;

	@Autowired
	JdbcTemplate jdbc;

	/** The database is shared: accounts other tests left QUEUED must not be picked before ours. */
	@BeforeEach
	void drainQueue() {
		while (scanWorker.runOnce()) {
			// each call scans one waiting account against the empty fake mailbox
		}
	}

	private static Attachment pdf(String part, String name) {
		return new Attachment(part, "att-" + part, name, "application/pdf", BIG, false);
	}

	private void add(String address, String id, String subject, Instant at, Attachment... attachments) {
		google.addEmail(address, new Email(id, "Shop <shop@example.com>", subject, "preview", at, List.of(attachments)),
				Map.of());
	}

	private GmailAccount account(String id) {
		return accounts.findById(UUID.fromString(id)).orElseThrow();
	}

	private List<GmailEmail> emailsOf(String accountId) {
		return emails.findAll().stream().filter(e -> e.getAccount().getId().toString().equals(accountId)).toList();
	}

	/** Plain SQL: the entities' lazy links need a session, which a test method does not have. */
	private List<Map<String, Object>> filesOf(String accountId) {
		return jdbc.queryForList("select f.file_name, f.status from gmail_files f join gmail_emails e"
				+ " on e.id = f.email_id where e.account_id = ?", UUID.fromString(accountId));
	}

	private String connectAndScan(String address, String name) throws Exception {
		String id = connect(registerAndGetToken(uniqueEmail(name)), address);
		assertThat(scanWorker.runOnce()).isTrue();
		return id;
	}

	@Test
	void scanSavesEmailsWithBillFiles() throws Exception {
		String address = "scan1@gmail.com";
		add(address, "m1", "Your invoice", Instant.now().minusSeconds(30), pdf("1", "invoice.pdf"));
		add(address, "m2", "Weekly newsletter", Instant.now().minusSeconds(20), pdf("1", "deals.pdf"));
		add(address, "m3", "Your invoice docs", Instant.now().minusSeconds(10),
				new Attachment("1", "a", "notes.docx", "application/vnd.openxmlformats", BIG, false));

		String id = connectAndScan(address, "s1");

		assertThat(emailsOf(id)).hasSize(1);
		assertThat(emailsOf(id).get(0).getKind()).isEqualTo(EmailKind.INVOICE);
		assertThat(emailsOf(id).get(0).getFromEmail()).isEqualTo("shop@example.com");
		assertThat(filesOf(id)).hasSize(1);
		assertThat(filesOf(id).get(0).get("status")).isEqualTo("NEW");
		assertThat(account(id).getScanStatus()).isEqualTo(GmailScanStatus.IDLE);
		assertThat(account(id).getLastScannedAt()).isNotNull();
	}

	@Test
	void secondScanAddsOnlyNewEmails() throws Exception {
		String address = "scan2@gmail.com";
		String token = registerAndGetToken(uniqueEmail("s2"));
		add(address, "m1", "Your invoice", Instant.now().minusSeconds(30), pdf("1", "invoice.pdf"));
		String id = connect(token, address);
		scanWorker.runOnce();
		assertThat(emailsOf(id)).hasSize(1);

		add(address, "m2", "Another invoice", Instant.now().minusSeconds(10), pdf("1", "invoice2.pdf"));
		mvc.perform(post(GMAIL + "/accounts/" + id + "/scan").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"range\":\"ONE_YEAR\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scanStatus").value("QUEUED"));
		scanWorker.runOnce();

		assertThat(emailsOf(id)).hasSize(2);
		assertThat(emailsOf(id)).extracting(GmailEmail::getGmailMessageId).containsExactlyInAnyOrder("m1", "m2");
	}

	@Test
	void scanRangeBecomesAfterDate() throws Exception {
		String token = registerAndGetToken(uniqueEmail("s3"));
		String id = connect(token, "scan3@gmail.com");
		scanWorker.runOnce();

		mvc.perform(post(GMAIL + "/accounts/" + id + "/scan").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"range\":\"TWO_YEARS\"}"))
				.andExpect(status().isOk());
		scanWorker.runOnce();

		String expected = LocalDate.now().minusMonths(24).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
		assertThat(google.lastQuery()).startsWith("after:" + expected);
	}

	@Test
	void inlineLogosAreNotOffered() throws Exception {
		String address = "scan4@gmail.com";
		add(address, "m1", "Your invoice", Instant.now(), pdf("1", "invoice.pdf"),
				new Attachment("2", "a2", "image001.png", "image/png", 5 * 1024, true),
				new Attachment("3", "a3", "logo.png", "image/png", 3 * 1024, false));

		String id = connectAndScan(address, "s4");

		assertThat(filesOf(id)).extracting(f -> f.get("file_name")).containsExactly("invoice.pdf");
	}

	@Test
	void octetStreamPdfByExtensionIsKept() throws Exception {
		String address = "scan5@gmail.com";
		add(address, "m1", "Your invoice", Instant.now(),
				new Attachment("1", "a1", "bill.PDF", "application/octet-stream", BIG, false));

		String id = connectAndScan(address, "s5");

		assertThat(filesOf(id)).extracting(f -> f.get("file_name")).containsExactly("bill.PDF");
	}

	@Test
	void revokedAccessShowsError() throws Exception {
		String id = connect(registerAndGetToken(uniqueEmail("s6")), "scan6@gmail.com");
		google.failNext(new GoogleApiException("revoked", GoogleApiException.Kind.REVOKED, null));

		scanWorker.runOnce();

		assertThat(account(id).getScanStatus()).isEqualTo(GmailScanStatus.ERROR);
		assertThat(account(id).getLastError()).isEqualTo("Gmail access was removed. Connect this account again.");
	}

	@Test
	void scanWhileRunningIs409() throws Exception {
		String token = registerAndGetToken(uniqueEmail("s7"));
		String id = connect(token, "scan7@gmail.com"); // connecting queues a scan

		mvc.perform(post(GMAIL + "/accounts/" + id + "/scan").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"range\":\"ONE_YEAR\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GMAIL_SCAN_RUNNING"))
				.andExpect(jsonPath("$.message").value("A scan is already running for this account."));
	}

	@Test
	void disconnectDuringScanDropsTheResult() throws Exception {
		String token = registerAndGetToken(uniqueEmail("s8"));
		String id = connect(token, "scan8@gmail.com");
		Optional<ScanJob> job = gmailService.claimNextScan();
		assertThat(job).isPresent();

		mvc.perform(delete(GMAIL + "/accounts/" + id).header("Authorization", bearer(token)))
				.andExpect(status().isNoContent());
		Email email = new Email("m1", "a@b.com", "Invoice", "x", Instant.now(), List.of(pdf("1", "invoice.pdf")));
		gmailService.saveScan(job.get().accountId(),
				List.of(new GmailService.FoundEmail(email, EmailKind.INVOICE, email.attachments())));

		assertThat(accounts.findById(UUID.fromString(id))).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from gmail_emails where account_id = ?", Integer.class,
				UUID.fromString(id))).isZero();
	}

	@Test
	void stuckScanIsRequeued() throws Exception {
		String id = connect(registerAndGetToken(uniqueEmail("s9")), "scan9@gmail.com");
		GmailAccount account = account(id);
		account.startScan();
		accounts.save(account);

		gmailService.resetStuckScans();

		assertThat(account(id).getScanStatus()).isEqualTo(GmailScanStatus.QUEUED);
	}

	@Test
	void undecryptableTokenEndsInErrorAndQueueMovesOn() throws Exception {
		String first = connect(registerAndGetToken(uniqueEmail("s11")), "scan11@gmail.com");
		String second = connect(registerAndGetToken(uniqueEmail("s12")), "scan12@gmail.com");
		GmailAccount broken = account(first);
		broken.updateToken(new byte[] {1, 2, 3});
		accounts.save(broken);
		GmailAccount healthy = account(second); // saving moved "broken" last in line, so touch this one after it
		healthy.queueScan(LocalDate.now().minusMonths(6));
		accounts.save(healthy);

		assertThat(scanWorker.runOnce()).isFalse(); // does not throw
		assertThat(account(first).getScanStatus()).isEqualTo(GmailScanStatus.ERROR);
		assertThat(account(first).getLastError()).isEqualTo("The scan failed. Please try again.");

		assertThat(scanWorker.runOnce()).isTrue();
		assertThat(account(second).getScanStatus()).isEqualTo(GmailScanStatus.IDLE);
	}

	@Test
	void badRangeIs400() throws Exception {
		String token = registerAndGetToken(uniqueEmail("s10"));
		String id = connect(token, "scan10@gmail.com");
		scanWorker.runOnce();

		mvc.perform(post(GMAIL + "/accounts/" + id + "/scan").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"range\":\"TEN_YEARS\"}"))
				.andExpect(status().isBadRequest());
	}
}
