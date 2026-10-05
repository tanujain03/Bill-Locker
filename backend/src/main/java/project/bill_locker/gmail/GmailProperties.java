package project.bill_locker.gmail;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gmail import settings (application.properties, filled from backend/.env). Gmail import
 * only works once the Google OAuth client and the token key are set;
 * docs/step-7-codes-and-gmail.md explains how to get them.
 *
 * @param clientId     GOOGLE_CLIENT_ID, from the Google Cloud console
 * @param clientSecret GOOGLE_CLIENT_SECRET, from the same place
 * @param tokenKey     GMAIL_TOKEN_KEY: 32 random bytes in Base64, encrypts the stored Google tokens
 * @param redirectUri  where Google sends the browser back to (must also be listed in the console)
 * @param frontendUrl  the app, where the browser goes after connecting
 * @param scanEnabled  false switches the background scanner off (tests start it themselves)
 */
@ConfigurationProperties("app.gmail")
public record GmailProperties(String clientId, String clientSecret, String tokenKey, String redirectUri,
		String frontendUrl, boolean scanEnabled) {

	public boolean isConfigured() {
		return problems().isEmpty();
	}

	/** What is missing or wrong, by setting name (never the values); empty when Gmail import can run. */
	public List<String> problems() {
		List<String> problems = new ArrayList<>();
		if (!hasText(clientId)) {
			problems.add("GOOGLE_CLIENT_ID is not set");
		}
		if (!hasText(clientSecret)) {
			problems.add("GOOGLE_CLIENT_SECRET is not set");
		}
		if (!hasText(tokenKey)) {
			problems.add("GMAIL_TOKEN_KEY is not set");
		}
		else if (!isAesKey(tokenKey)) {
			problems.add("GMAIL_TOKEN_KEY must be 32 random bytes in Base64 (44 characters)");
		}
		return problems;
	}

	private static boolean isAesKey(String base64) {
		try {
			return Base64.getDecoder().decode(base64.strip()).length == 32;
		}
		catch (IllegalArgumentException notBase64) {
			return false;
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
