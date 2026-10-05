package project.bill_locker.gmail;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import project.bill_locker.document.DocumentSummary;
import project.bill_locker.document.DocumentType;

/** The shapes of the Gmail endpoints' requests and answers (docs/api-contract.md §13). */
public final class GmailResponses {

	private GmailResponses() {
	}

	/** {@code GET /api/integrations/gmail}. Not connected: connected=false and everything else empty. */
	public record ConnectionResponse(boolean connected, String email, Instant connectedAt, Instant lastSyncedAt,
			boolean autoSync, GmailSyncStatus syncStatus, String lastError) {

		static final ConnectionResponse NOT_CONNECTED =
				new ConnectionResponse(false, null, null, null, false, GmailSyncStatus.IDLE, null);

		static ConnectionResponse from(GmailConnection connection) {
			return new ConnectionResponse(true, connection.getEmail(), connection.getCreatedAt(),
					connection.getLastSyncedAt(), connection.isAutoSync(), connection.getSyncStatus(),
					connection.getLastError());
		}
	}

	/** Where the browser goes to give Bill Locker read access to Gmail. */
	public record ConnectResponse(String authorizationUrl) {
	}

	/** An attachment as the app shows it (Gmail's own attachment id stays on the server). */
	public record AttachmentResponse(String fileName, String mimeType, long size) {
	}

	/** One shortlisted email. {@code documentIds}: the documents imported from it. */
	public record MessageResponse(UUID id, String fromName, String fromEmail, String subject, String snippet,
			Instant receivedAt, List<AttachmentResponse> attachments, DocumentType detectedType, double confidence,
			GmailMessageStatus status, List<UUID> documentIds) {

		static MessageResponse from(GmailMessage message, List<UUID> documentIds) {
			String fromName = message.getFromName() == null || message.getFromName().isBlank()
					? message.getFromEmail() : message.getFromName();
			return new MessageResponse(message.getId(), fromName, message.getFromEmail(), message.getSubject(),
					message.getSnippet(), message.getReceivedAt(),
					message.getAttachments().stream()
							.map(file -> new AttachmentResponse(file.fileName(), file.mimeType(), file.size()))
							.toList(),
					message.getDetectedType(), message.getConfidence(), message.getStatus(), documentIds);
		}
	}

	public record ImportRequest(List<UUID> messageIds) {
	}

	public record ImportResponse(List<DocumentSummary> documents) {
	}
}
