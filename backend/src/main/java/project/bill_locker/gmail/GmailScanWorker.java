package project.bill_locker.gmail;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import project.bill_locker.gmail.GmailService.FoundEmail;
import project.bill_locker.gmail.GmailService.ScanJob;
import project.bill_locker.gmail.GoogleApi.Attachment;
import project.bill_locker.gmail.GoogleApi.Email;
import project.bill_locker.gmail.GoogleApi.GoogleApiException;

/**
 * Scans one queued mailbox per call (the timer lives in WorkerSchedule). This is the only
 * place that talks to Google during a scan; the database steps are short methods on
 * GmailService, so no transaction stays open while Google is slow.
 */
@Component
public class GmailScanWorker {

	private static final Logger log = LoggerFactory.getLogger(GmailScanWorker.class);

	/** Newest emails looked at per scan. */
	static final int MAX_EMAILS = 200;

	// Same limit as uploads (DocumentService.MAX_FILE_BYTES is package-private there).
	private static final long MAX_FILE_BYTES = 10 * 1024 * 1024;
	// Logos and signatures are small images; a scanned bill photo is not.
	private static final long MIN_IMAGE_BYTES = 20 * 1024;
	private static final Set<String> TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");
	private static final Set<String> EXTENSIONS = Set.of(".pdf", ".jpg", ".jpeg", ".png", ".webp");
	private static final DateTimeFormatter QUERY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final GmailService gmail;
	private final GoogleApi google;
	private final EmailClassifier classifier;

	public GmailScanWorker(GmailService gmail, GoogleApi google, EmailClassifier classifier) {
		this.gmail = gmail;
		this.google = google;
		this.classifier = classifier;
	}

	static String query(LocalDate since) {
		return "after:" + since.format(QUERY_DATE) + " has:attachment (invoice OR receipt OR bill OR warranty"
				+ " OR guarantee OR service OR \"order confirmation\" OR \"tax invoice\")";
	}

	/** Returns false when no scan was waiting. */
	public boolean runOnce() {
		Optional<ScanJob> claimed = gmail.claimNextScan();
		if (claimed.isEmpty()) {
			return false;
		}
		ScanJob job = claimed.get();
		try {
			gmail.saveScan(job.accountId(), scan(job));
		} catch (GoogleApiException e) {
			gmail.failScan(job.accountId(), e.kind() == GoogleApiException.Kind.REVOKED
					? "Gmail access was removed. Connect this account again."
					: "Gmail could not be reached. Try Scan again later.");
		} catch (RuntimeException e) {
			log.error("Gmail scan failed", e); // the stack trace, never the token
			gmail.failScan(job.accountId(), "The scan failed. Please try again.");
		}
		return true;
	}

	private List<FoundEmail> scan(ScanJob job) {
		String accessToken = google.accessToken(job.refreshToken());
		List<FoundEmail> found = new ArrayList<>();
		for (String id : google.searchMessages(accessToken, query(job.since()), MAX_EMAILS)) {
			if (job.knownMessageIds().contains(id)) {
				continue; // saved by an earlier scan
			}
			Email email;
			try {
				email = google.message(accessToken, id);
			} catch (GoogleApiException e) {
				if (e.kind() == GoogleApiException.Kind.NOT_FOUND) {
					continue; // deleted since the search: nothing to show
				}
				throw e;
			}
			List<Attachment> files = email.attachments().stream().filter(GmailScanWorker::isBillFile).toList();
			if (files.isEmpty()) {
				continue;
			}
			List<String> names = files.stream().map(Attachment::fileName).toList();
			classifier.classify(email.subject(), email.snippet(), names)
					.ifPresent(kind -> found.add(new FoundEmail(email, kind, files)));
		}
		return found;
	}

	/** A real, importable bill: not an inline picture, a type we can read, small enough, not a logo. */
	static boolean isBillFile(Attachment file) {
		if (file.inline() || file.fileName() == null || file.fileName().isBlank()
				|| file.sizeBytes() > MAX_FILE_BYTES) {
			return false;
		}
		String name = file.fileName().toLowerCase(Locale.ROOT);
		String type = file.contentType() == null ? "" : file.contentType().toLowerCase(Locale.ROOT);
		boolean byType = TYPES.contains(type);
		// Some senders label everything application/octet-stream, so the extension decides then.
		boolean byExtension = type.equals("application/octet-stream") && EXTENSIONS.stream().anyMatch(name::endsWith);
		if (!byType && !byExtension) {
			return false;
		}
		boolean pdf = type.equals("application/pdf") || byExtension && name.endsWith(".pdf");
		return pdf || file.sizeBytes() >= MIN_IMAGE_BYTES;
	}
}
