package project.bill_locker.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class GmailPropertiesTests {

	private static String key(int bytes) {
		return Base64.getEncoder().encodeToString(new byte[bytes]);
	}

	@Test
	void configuredWithAllKeys() {
		assertThat(new GmailProperties("id", "secret", key(32), "http://x/cb").isConfigured()).isTrue();
	}

	@Test
	void blankKeyIsNotConfigured() {
		assertThat(new GmailProperties("id", "secret", "", "http://x/cb").isConfigured()).isFalse();
		assertThat(new GmailProperties("", "secret", key(32), "http://x/cb").isConfigured()).isFalse();
	}

	@Test
	void shortKeyIsNotConfigured() {
		assertThat(new GmailProperties("id", "secret", key(16), "http://x/cb").isConfigured()).isFalse();
		assertThat(new GmailProperties("id", "secret", "not base64!", "http://x/cb").isConfigured()).isFalse();
	}
}
