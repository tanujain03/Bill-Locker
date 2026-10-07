package project.bill_locker.gmail;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.DocumentService;
import project.bill_locker.gmail.GmailService.ImportJob;
import project.bill_locker.gmail.GoogleApi.Attachment;
import project.bill_locker.gmail.GoogleApi.Email;
import project.bill_locker.gmail.GoogleApi.GoogleApiException;

/**
 * Downloads the attachments of files the user chose to import (the timer lives in
 * WorkerSchedule). Like the scan worker, Google is only called here, never inside a
 * transaction. The document is created queued, so Gemini reads it later, in the background.
 */
@Component
public class GmailImportWorker {

	private static final Logger log = LoggerFactory.getLogger(GmailImportWorker.class);

	/** Files taken per run: a few at a time keeps one run short. */
	private static final int MAX_PER_RUN = 5;

	private final GmailService gmail;
	private final GoogleApi google;
	private final DocumentService documents;

	public GmailImportWorker(GmailService gmail, GoogleApi google, DocumentService documents) {
		this.gmail = gmail;
		this.google = google;
		this.documents = documents;
	}

	/** Returns how many files were finished (imported or failed). */
	public int runOnce() {
		int handled = 0;
		for (ImportJob job : gmail.claimImports(MAX_PER_RUN)) {
			try {
				byte[] bytes = download(job);
				var document = documents.createFromBytes(job.userId(), job.fileName(), bytes, job.accountEmail());
				gmail.importDone(job.fileId(), document.id());
			} catch (GoogleApiException e) {
				if (e.kind() == GoogleApiException.Kind.UNAVAILABLE) {
					return handled; // Gmail is down: leave it IMPORTING, the next run tries again
				}
				if (e.kind() == GoogleApiException.Kind.REVOKED) {
					gmail.importFailed(job.fileId(), "Gmail access was removed. Connect this account again.");
					gmail.failScan(job.accountId(), "Gmail access was removed. Connect this account again.");
				} else {
					gmail.importFailed(job.fileId(), "This file is no longer in Gmail.");
				}
			} catch (MissingPart e) {
				gmail.importFailed(job.fileId(), "This file is no longer in Gmail.");
			} catch (ApiException e) {
				boolean unreadable = "INVALID_FILE_TYPE".equals(e.getCode()) || "FILE_EMPTY".equals(e.getCode());
				gmail.importFailed(job.fileId(),
						unreadable ? "This file isn't a PDF or image Bill Locker can read." : e.getMessage());
			} catch (RuntimeException e) {
				log.error("Gmail import failed", e); // the stack trace, never the token
				gmail.importFailed(job.fileId(), "The import failed. Please try again.");
			}
			handled++;
		}
		return handled;
	}

	private byte[] download(ImportJob job) {
		String accessToken = google.accessToken(job.refreshToken());
		Email email = google.message(accessToken, job.gmailMessageId());
		// partId, not the attachment id: Gmail hands out a different attachment id on every fetch.
		Optional<Attachment> part = email.attachments().stream().filter(a -> job.partId().equals(a.partId()))
				.findFirst();
		return google.attachment(accessToken, job.gmailMessageId(),
				part.orElseThrow(MissingPart::new).attachmentId());
	}

	private static class MissingPart extends RuntimeException {
		MissingPart() {
			super("attachment part not in the email", null, false, false);
		}
	}
}
