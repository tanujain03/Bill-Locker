package project.bill_locker.gmail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.DocumentRepository;
import project.bill_locker.gmail.GmailViews.Counts;
import project.bill_locker.gmail.GmailViews.EmailView;
import project.bill_locker.gmail.GmailViews.GmailEmailView;
import project.bill_locker.gmail.GmailViews.GmailFileView;
import project.bill_locker.gmail.GmailViews.GmailAccountView;
import project.bill_locker.gmail.GmailViews.GmailOverview;
import project.bill_locker.gmail.GmailViews.ScanRange;

/** The rules for Gmail import: what is configured, who may see which account. */
@Service
public class GmailService {

	private final GmailProperties properties;
	private final GmailAccountRepository accounts;
	private final GmailFileRepository files;
	private final GmailEmailRepository emails;
	private final TokenCipher cipher;
	private final DocumentRepository documents;

	public GmailService(GmailProperties properties, GmailAccountRepository accounts, GmailFileRepository files,
			GmailEmailRepository emails, TokenCipher cipher, DocumentRepository documents) {
		this.properties = properties;
		this.accounts = accounts;
		this.files = files;
		this.emails = emails;
		this.cipher = cipher;
		this.documents = documents;
	}

	/** What the scan worker needs, so it can talk to Google without holding a transaction. */
	public record ScanJob(UUID accountId, String refreshToken, LocalDate since, Set<String> knownMessageIds) {

		@Override
		public String toString() { // never print the token
			return "ScanJob[accountId=" + accountId + "]";
		}
	}

	/** An email the worker found, already classified, with the attachments worth offering. */
	public record FoundEmail(GoogleApi.Email email, EmailKind kind, List<GoogleApi.Attachment> files) {
	}

	/** Answers even when Gmail is off ("configured: false"), so the page can explain it. */
	@Transactional(readOnly = true)
	public GmailOverview overview(UUID userId) {
		List<GmailAccountView> views = accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
				.map(GmailAccountView::of).toList();
		Counts counts = new Counts(
				files.countByUserAndStatusIn(userId, EmailView.TO_REVIEW.statuses()),
				files.countByUserAndStatusIn(userId, EmailView.IGNORED.statuses()),
				files.countByUserAndStatusIn(userId, EmailView.IMPORTED.statuses()));
		return new GmailOverview(properties.isConfigured(), views, counts);
	}

	public boolean isConfigured() {
		return properties.isConfigured();
	}

	public void requireConfigured() {
		if (!properties.isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GMAIL_NOT_CONFIGURED",
					"Gmail import isn't set up on this server yet.");
		}
	}

	/** Another user's account looks exactly like one that does not exist. */
	public GmailAccount findAccount(UUID userId, UUID id) {
		return accounts.findByIdAndUserId(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
				"GMAIL_ACCOUNT_NOT_FOUND", "That Gmail account was not found."));
	}

	/** Asks the worker to scan; a scan already waiting or running is not stacked on. */
	@Transactional
	public GmailAccountView queueScan(UUID userId, UUID accountId, ScanRange range) {
		GmailAccount account = findAccount(userId, accountId);
		if (account.getScanStatus() == GmailScanStatus.QUEUED || account.getScanStatus() == GmailScanStatus.SCANNING) {
			throw new ApiException(HttpStatus.CONFLICT, "GMAIL_SCAN_RUNNING",
					"A scan is already running for this account.");
		}
		account.queueScan(range.since(LocalDate.now()));
		return GmailAccountView.of(accounts.save(account));
	}

	/** Takes the oldest waiting scan (QUEUED -> SCANNING) and decrypts its token for the worker. */
	@Transactional
	public Optional<ScanJob> claimNextScan() {
		return accounts.findFirstByScanStatusOrderByUpdatedAtAsc(GmailScanStatus.QUEUED).flatMap(account -> {
			String token;
			try {
				token = cipher.decrypt(account.getRefreshTokenCiphertext());
			} catch (RuntimeException e) {
				// Wrong or rotated key: end this scan in ERROR here, or the same account would be retried forever.
				account.failScan("The scan failed. Please try again.");
				return Optional.<ScanJob>empty();
			}
			account.startScan();
			return Optional.of(new ScanJob(account.getId(), token, sinceOf(account),
					new HashSet<>(emails.findMessageIdsByAccount(account.getId()))));
		});
	}

	/** Stores what the scan found. If the user disconnected meanwhile, the result is simply dropped. */
	@Transactional
	public void saveScan(UUID accountId, List<FoundEmail> found) {
		GmailAccount account = accounts.findById(accountId).orElse(null);
		if (account == null) {
			return;
		}
		Set<String> stored = new HashSet<>(emails.findMessageIdsByAccount(accountId));
		for (FoundEmail item : found) {
			GoogleApi.Email email = item.email();
			if (!stored.add(email.id())) {
				continue; // already saved: the unique key would reject it
			}
			String[] from = splitSender(email.from());
			GmailEmail saved = emails.save(new GmailEmail(account, email.id(), cut(from[0], 200), cut(from[1], 254),
					cut(email.subject(), 500), cut(email.snippet(), 500),
					email.receivedAt() != null ? email.receivedAt() : Instant.now(), item.kind()));
			for (GoogleApi.Attachment file : item.files()) {
				files.save(new GmailFile(saved, cut(file.partId(), 32), cut(file.fileName(), 255),
						cut(file.contentType(), 100), file.sizeBytes()));
			}
		}
		account.finishScan();
	}

	/** The reason is words the user can act on; the account may be gone already. */
	@Transactional
	public void failScan(UUID accountId, String reason) {
		accounts.findById(accountId).ifPresent(account -> account.failScan(reason));
	}

	/** The emails of one tab, newest first, each with only its files in that tab. */
	@Transactional(readOnly = true)
	public List<GmailEmailView> emails(UUID userId, EmailView view) {
		Map<UUID, List<GmailFile>> byEmail = new LinkedHashMap<>(); // keeps the query's newest-first order
		for (GmailFile file : files.findForView(userId, view.statuses())) {
			byEmail.computeIfAbsent(file.getEmail().getId(), id -> new ArrayList<>()).add(file);
		}
		return byEmail.values().stream().map(list -> {
			GmailEmail e = list.get(0).getEmail();
			return new GmailEmailView(e.getId(), e.getAccount().getEmail(), e.getFromName(), e.getFromEmail(),
					e.getSubject(), e.getSnippet(), e.getReceivedAt(), e.getKind(),
					list.stream().map(GmailViews::fileView).toList());
		}).toList();
	}

	/** Import / ignore / restore: only the transitions that make sense, the rest are left as they are. */
	@Transactional
	public List<GmailFileView> importFiles(UUID userId, List<UUID> ids) {
		return change(userId, ids, f -> {
			boolean deletedDocument = f.getStatus() == GmailFileStatus.IMPORTED && f.getDocument() == null;
			if (f.getStatus() == GmailFileStatus.NEW || f.getStatus() == GmailFileStatus.IGNORED
					|| f.getStatus() == GmailFileStatus.FAILED || deletedDocument) {
				f.queueImport(); // IMPORTING stays as it is, so a double click cannot make two documents
			}
		});
	}

	@Transactional
	public List<GmailFileView> ignoreFiles(UUID userId, List<UUID> ids) {
		return change(userId, ids, f -> {
			if (f.getStatus() == GmailFileStatus.NEW || f.getStatus() == GmailFileStatus.FAILED) {
				f.ignore();
			}
		});
	}

	@Transactional
	public List<GmailFileView> restoreFiles(UUID userId, List<UUID> ids) {
		return change(userId, ids, f -> {
			if (f.getStatus() == GmailFileStatus.IGNORED) {
				f.restore();
			}
		});
	}

	/** All ids are checked before anything changes: one stranger's id means 404 and no change. */
	private List<GmailFileView> change(UUID userId, List<UUID> ids, Consumer<GmailFile> action) {
		Set<UUID> wanted = new LinkedHashSet<>(ids);
		Map<UUID, GmailFile> found = new HashMap<>();
		files.findOwned(wanted, userId).forEach(f -> found.put(f.getId(), f));
		if (found.size() != wanted.size()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "GMAIL_FILE_NOT_FOUND", "That file was not found.");
		}
		return wanted.stream().map(found::get).peek(action).map(GmailViews::fileView).toList();
	}

	/** What the import worker needs for one file, so it can talk to Google without a transaction. */
	public record ImportJob(UUID fileId, UUID userId, String accountEmail, String refreshToken, String gmailMessageId,
			String partId, String fileName, UUID accountId) {

		@Override
		public String toString() { // never print the token
			return "ImportJob[fileId=" + fileId + "]";
		}
	}

	/**
	 * The oldest files waiting to be imported. Their status stays IMPORTING until done or failed, and the
	 * one scheduler thread runs one worker at a time, so a file is never taken twice.
	 */
	@Transactional
	public List<ImportJob> claimImports(int max) {
		List<ImportJob> jobs = new ArrayList<>();
		for (GmailFile file : files.findByStatusOldestFirst(GmailFileStatus.IMPORTING, PageRequest.of(0, max))) {
			GmailAccount account = file.getEmail().getAccount();
			try {
				jobs.add(new ImportJob(file.getId(), file.getEmail().getUser().getId(), account.getEmail(),
						cipher.decrypt(account.getRefreshTokenCiphertext()), file.getEmail().getGmailMessageId(),
						file.getPartId(), file.getFileName(), account.getId()));
			} catch (RuntimeException e) {
				// Wrong or rotated key: fail it here, or it would sit at the front of the queue forever.
				file.failImport("The import failed. Please try again.");
			}
		}
		return jobs;
	}

	/** If the user disconnected meanwhile the file is gone and there is nothing to update. */
	@Transactional
	public void importDone(UUID fileId, UUID documentId) {
		files.findById(fileId).ifPresent(file -> file.finishImport(documents.getReferenceById(documentId)));
	}

	@Transactional
	public void importFailed(UUID fileId, String reason) {
		files.findById(fileId).ifPresent(file -> file.failImport(reason));
	}

	/** A scan cut off by a restart would stay SCANNING forever, so put it back in the queue. */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void resetStuckScans() {
		accounts.findByScanStatus(GmailScanStatus.SCANNING).forEach(account -> account.queueScan(sinceOf(account)));
	}

	private static LocalDate sinceOf(GmailAccount account) {
		return account.getScanSince() != null ? account.getScanSince() : ScanRange.ONE_YEAR.since(LocalDate.now());
	}

	/** "Shop Name <shop@x.com>" gives [name, address]; a bare address has no name. */
	private static String[] splitSender(String from) {
		if (from == null) {
			return new String[] {null, null};
		}
		int open = from.lastIndexOf('<');
		int close = from.lastIndexOf('>');
		if (open >= 0 && close > open) {
			String name = from.substring(0, open).replace("\"", "").trim();
			return new String[] {name.isEmpty() ? null : name, from.substring(open + 1, close).trim()};
		}
		return new String[] {null, from.trim()};
	}

	private static String cut(String text, int max) {
		return text == null || text.length() <= max ? text : text.substring(0, max);
	}
}
