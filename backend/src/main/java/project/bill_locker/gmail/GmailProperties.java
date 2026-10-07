package project.bill_locker.gmail;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Google sign-in settings for Gmail import (all from backend/.env). */
@ConfigurationProperties("app.gmail")
public record GmailProperties(String clientId, String clientSecret, String tokenKey, String redirectUri) {

	/** All four set and the token key is 32 bytes in base64 (the AES-256 key size). */
	public boolean isConfigured() {
		return !blank(clientId) && !blank(clientSecret) && !blank(redirectUri) && keyBytes() != null;
	}

	/** The decoded key, or null when it is missing, not base64 or not 32 bytes. */
	byte[] keyBytes() {
		if (blank(tokenKey)) {
			return null;
		}
		try {
			byte[] key = Base64.getDecoder().decode(tokenKey.trim());
			return key.length == 32 ? key : null;
		} catch (IllegalArgumentException notBase64) {
			return null;
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
