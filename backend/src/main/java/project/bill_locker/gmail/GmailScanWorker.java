package project.bill_locker.gmail;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.bill_locker.gmail.GmailService.ScanJob;
import project.bill_locker.gmail.GoogleApi.Email;
import project.bill_locker.gmail.GoogleApi.GoogleApiException;

/**
 * Scans connected inboxes in the background, like the document reader (step 4): every
 * few seconds it takes the connections waiting to be scanned (SYNCING). The slow Gmail
 * calls happen outside any database transaction; GmailService saves the results.
 */
@Component
public class GmailScanWorker {

	/** Recent emails that mention a bill and have a file attached. */
	static final String QUERY = "newer_than:1y has:attachment (invoice OR receipt OR bill OR \"order confirmation\" OR warranty)";
	private static final int MAX_EMAILS = 50;
	private static final Logger log = LoggerFactory.getLogger(GmailScanWorker.class);

	private final GmailService gmail;
	private final GoogleApi google;
	private final GmailProperties properties;

	public GmailScanWorker(GmailService gmail, GoogleApi google, GmailProperties properties) {
		this.gmail = gmail;
		this.google = google;
		this.properties = properties;
	}

	@Scheduled(fixedDelay = 3000, initialDelay = 10000)
	public void onSchedule() {
		if (properties.scanEnabled() && properties.isConfigured()) {
			scanPending();
		}
	}

	/** Every morning at 07:30: inboxes with auto-scan switched on get a scan. */
	@Scheduled(cron = "0 30 7 * * *")
	public void everyMorning() {
		if (properties.scanEnabled() && properties.isConfigured()) {
			gmail.startDailyScans();
		}
	}

	/** Runs every waiting scan; returns how many. */
	public int scanPending() {
		List<ScanJob> jobs = gmail.pendingScans();
		jobs.forEach(this::scan);
		return jobs.size();
	}

	private void scan(ScanJob job) {
		try {
			String accessToken = google.accessToken(job.refreshToken());
			List<Email> emails = new ArrayList<>();
			for (String id : google.searchMessages(accessToken, QUERY, MAX_EMAILS)) {
				if (!job.knownMessageIds().contains(id)) { // only fetch emails we haven't seen yet
					emails.add(google.message(accessToken, id));
				}
			}
			gmail.saveScan(job.userId(), emails);
			log.info("Scanned Gmail for user {}: {} new email(s) looked at", job.userId(), emails.size());
		}
		catch (GoogleApiException ex) {
			log.warn("Gmail scan for user {} failed: {}", job.userId(), ex.getMessage());
			gmail.failScan(job.userId(), ex.isAccessRevoked()
					? "Gmail access was removed. Disconnect Gmail and connect it again."
					: "Gmail could not be reached. Try \"Scan now\" again later.");
		}
		catch (RuntimeException ex) {
			log.error("Gmail scan for user {} failed", job.userId(), ex);
			gmail.failScan(job.userId(), "The scan failed. Please try again.");
		}
	}
}
