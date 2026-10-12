package project.bill_locker.gmail;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encrypts Google refresh tokens before they reach the database (AES-256-GCM).
 * GCM also detects tampering. Output = 12-byte random nonce + ciphertext + tag.
 * Without a usable key the app still starts; only encrypt/decrypt fail.
 */
@Component
class TokenCipher {

	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecureRandom random = new SecureRandom();
	private final byte[] key; // null = Gmail not configured

	@Autowired // two constructors: tell Spring which one to use
	TokenCipher(GmailProperties properties) {
		this.key = properties.keyBytes();
	}

	// For unit tests: use a key directly.
	TokenCipher(byte[] key) {
		this.key = key;
	}

	byte[] encrypt(String plain) {
		requireKey();
		try {
			// A new nonce every time, so the same token never encrypts to the same bytes.
			byte[] nonce = new byte[NONCE_BYTES];
			random.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] result = Arrays.copyOf(nonce, NONCE_BYTES + encrypted.length);
			System.arraycopy(encrypted, 0, result, NONCE_BYTES, encrypted.length);
			return result;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not encrypt the token", e);
		}
	}

	String decrypt(byte[] data) {
		requireKey();
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
					new GCMParameterSpec(TAG_BITS, data, 0, NONCE_BYTES));
			return new String(cipher.doFinal(data, NONCE_BYTES, data.length - NONCE_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | RuntimeException e) {
			// Wrong key or changed bytes: never return garbage.
			throw new IllegalStateException("Could not decrypt the token", e);
		}
	}

	private void requireKey() {
		if (key == null) {
			throw new IllegalStateException("Gmail import is not configured");
		}
	}
}
