package project.bill_locker.gmail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts the Google refresh token before it is stored (AES-256-GCM). The key,
 * GMAIL_TOKEN_KEY, lives only in backend/.env: a copy of the database alone can't be
 * used to read anyone's email. GCM also detects any change to the stored bytes.
 */
@Component
public class TokenCipher {

	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final GmailProperties properties;
	private final SecureRandom random = new SecureRandom();

	public TokenCipher(GmailProperties properties) {
		this.properties = properties;
	}

	/** Returns nonce + ciphertext. A fresh random nonce each time, so equal tokens never look equal. */
	public byte[] encrypt(String token) {
		try {
			byte[] nonce = new byte[NONCE_BYTES];
			random.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
			byte[] sealed = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not encrypt the Gmail token", ex);
		}
	}

	public String decrypt(byte[] stored) {
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
			byte[] plain = cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not decrypt the Gmail token (was GMAIL_TOKEN_KEY changed?)", ex);
		}
	}

	private SecretKey key() {
		byte[] bytes = Base64.getDecoder().decode(properties.tokenKey().strip());
		if (bytes.length != 32) {
			throw new IllegalStateException("GMAIL_TOKEN_KEY must be 32 bytes in Base64 (44 characters).");
		}
		return new SecretKeySpec(bytes, "AES");
	}
}
