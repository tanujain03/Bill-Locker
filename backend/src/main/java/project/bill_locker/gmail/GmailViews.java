package project.bill_locker.gmail;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentStatus;

/** What the Gmail endpoints send and receive. Entities never leave the backend. */
public final class GmailViews {

	private GmailViews() {
	}

	/** How far back a scan looks. */
	public enum ScanRange {
		SIX_MONTHS(6), ONE_YEAR(12), TWO_YEARS(24), FIVE_YEARS(60);

		private final int months;

		ScanRange(int months) {
			this.months = months;
		}

		public LocalDate since(LocalDate today) {
			return today.minusMonths(months);
		}
	}

	/** The three tabs: which file statuses each one shows. */
	public enum EmailView {
		TO_REVIEW(List.of(GmailFileStatus.NEW, GmailFileStatus.IMPORTING, GmailFileStatus.FAILED)),
		IGNORED(List.of(GmailFileStatus.IGNORED)),
		IMPORTED(List.of(GmailFileStatus.IMPORTED));

		private final List<GmailFileStatus> statuses;

		EmailView(List<GmailFileStatus> statuses) {
			this.statuses = statuses;
		}

		public List<GmailFileStatus> statuses() {
			return statuses;
		}
	}

	public record ConnectResponse(String authorizationUrl) {
	}

	public record GmailOverview(boolean configured, List<GmailAccountView> accounts, Counts counts) {
	}

	/** The numbers on the three tabs (counted in files, not emails). */
	public record Counts(long toReview, long ignored, long imported) {
	}

	public record GmailAccountView(UUID id, String email, GmailScanStatus scanStatus, Instant lastScannedAt,
			String lastError, Instant connectedAt) {

		static GmailAccountView of(GmailAccount account) {
			return new GmailAccountView(account.getId(), account.getEmail(), account.getScanStatus(),
					account.getLastScannedAt(), account.getLastError(), account.getCreatedAt());
		}
	}

	public record GmailEmailView(UUID id, String accountEmail, String fromName, String fromEmail, String subject,
			String snippet, Instant receivedAt, EmailKind kind, List<GmailFileView> files) {
	}

	public record GmailFileView(UUID id, String fileName, String contentType, long sizeBytes, GmailFileStatus status,
			String error, DocumentRef document) {
	}

	public record DocumentRef(UUID id, DocumentStatus status, boolean readQueued) {
	}

	/** Needs the file's lazy document, so call it inside a transaction. */
	static GmailFileView fileView(GmailFile f) {
		Document d = f.getDocument();
		return new GmailFileView(f.getId(), f.getFileName(), f.getContentType(), f.getSizeBytes(), f.getStatus(),
				f.getError(), d == null ? null : new DocumentRef(d.getId(), d.getStatus(), d.getReadQueuedAt() != null));
	}

	public record ScanRequest(@NotNull ScanRange range) {
	}

	public record FileIdsRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> fileIds) {
	}
}
