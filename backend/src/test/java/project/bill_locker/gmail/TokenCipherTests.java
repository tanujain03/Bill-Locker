package project.bill_locker.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TokenCipherTests {

	private final TokenCipher cipher = new TokenCipher(new byte[32]);

	@Test
	void roundTrip() {
		assertThat(cipher.decrypt(cipher.encrypt("refresh-abc"))).isEqualTo("refresh-abc");
	}

	@Test
	void sameTextEncryptsDifferently() {
		assertThat(cipher.encrypt("refresh-abc")).isNotEqualTo(cipher.encrypt("refresh-abc"));
	}

	@Test
	void tamperedCipherFails() {
		byte[] encrypted = cipher.encrypt("refresh-abc");
		encrypted[encrypted.length - 1] ^= 1;
		assertThatThrownBy(() -> cipher.decrypt(encrypted)).isInstanceOf(RuntimeException.class);
	}

	@Test
	void unusableKeyOnlyFailsWhenUsed() {
		TokenCipher unconfigured = new TokenCipher(new GmailProperties("", "", "", ""));
		assertThatThrownBy(() -> unconfigured.encrypt("x"))
				.isInstanceOf(IllegalStateException.class).hasMessage("Gmail import is not configured");
		assertThatThrownBy(() -> unconfigured.decrypt(new byte[40]))
				.isInstanceOf(IllegalStateException.class).hasMessage("Gmail import is not configured");
	}

	@Test
	void keyFromPropertiesWorks() {
		String key = java.util.Base64.getEncoder().encodeToString(new byte[32]);
		TokenCipher fromProps = new TokenCipher(new GmailProperties("id", "secret", key, "http://x/cb"));
		assertThat(fromProps.decrypt(fromProps.encrypt("t"))).isEqualTo("t");
		assertThat(Arrays.equals(fromProps.encrypt("t"), fromProps.encrypt("t"))).isFalse();
	}
}
