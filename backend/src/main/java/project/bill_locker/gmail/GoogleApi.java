package project.bill_locker.gmail;

import java.time.Instant;
import java.util.List;

/**
 * Everything Bill Locker asks Google: sign-in (OAuth 2) and reading Gmail. It is an
 * interface so that tests can use a fake Gmail instead of the real one.
 */
public interface GoogleApi {

	/** The Google consent page the browser is sent to. */
	String authorizationUrl(String state, String codeChallenge);

	/** After the user agreed: trades the one-time code for tokens. */
	Tokens exchangeCode(String code, String codeVerifier);

	/** A fresh access token (valid about an hour) from the stored refresh token. */
	String accessToken(String refreshToken);

	String emailAddress(String accessToken);

	/** Ids of the newest messages that match a Gmail search, e.g. {@code has:attachment invoice}. */
	List<String> searchMessages(String accessToken, String query, int max);

	Email message(String accessToken, String messageId);

	byte[] attachment(String accessToken, String messageId, String attachmentId);

	/** Withdraws Bill Locker's access (users can also do this in their Google account). */
	void revoke(String refreshToken);

	/** The access token works for about an hour; the refresh token gets new ones later. */
	record Tokens(String accessToken, String refreshToken) {
	}

	/** What the scan needs from an email. The email's text itself is never stored. */
	record Email(String id, String from, String subject, String snippet, Instant receivedAt,
			List<GmailAttachment> attachments) {
	}

	/** Google said no or could not be reached. {@code accessRevoked}: the user withdrew our access. */
	class GoogleApiException extends RuntimeException {

		private final boolean accessRevoked;

		public GoogleApiException(String message, boolean accessRevoked, Throwable cause) {
			super(message, cause);
			this.accessRevoked = accessRevoked;
		}

		public boolean isAccessRevoked() {
			return accessRevoked;
		}
	}
}
