package project.bill_locker.gmail;

import java.time.Instant;
import java.util.List;

/**
 * Everything Bill Locker needs from Google, in one place so tests can swap it for a
 * fake (Google is never called in tests). Only the read-only Gmail scope is used.
 */
public interface GoogleApi {

	/** The page where the user agrees to let us read their mail. */
	String authorizationUrl(String state, String codeChallenge);

	/** Swaps the one-time code from Google's redirect for tokens. */
	Tokens exchangeCode(String code, String codeVerifier);

	/** A fresh short-lived access token. Throws REVOKED when the user withdrew access. */
	String accessToken(String refreshToken);

	/** The Gmail address the token belongs to. */
	String emailAddress(String accessToken);

	/** Ids of matching emails, newest first, at most {@code max}. */
	List<String> searchMessages(String accessToken, String query, int max);

	Email message(String accessToken, String messageId);

	byte[] attachment(String accessToken, String messageId, String attachmentId);

	/** Tells Google to forget our access. */
	void revoke(String refreshToken);

	/** refreshToken may be null (Google only sends it on the first consent). */
	record Tokens(String accessToken, String refreshToken) {
	}

	record Email(String id, String from, String subject, String snippet, Instant receivedAt,
			List<Attachment> attachments) {
	}

	record Attachment(String partId, String attachmentId, String fileName, String contentType, long sizeBytes,
			boolean inline) {
	}

	class GoogleApiException extends RuntimeException {

		/** REVOKED: access withdrawn or code invalid; NOT_FOUND: gone; UNAVAILABLE: try again later. */
		public enum Kind {
			REVOKED, NOT_FOUND, UNAVAILABLE
		}

		private final Kind kind;

		public GoogleApiException(String message, Kind kind, Throwable cause) {
			super(message, cause);
			this.kind = kind;
		}

		public Kind kind() {
			return kind;
		}
	}
}
