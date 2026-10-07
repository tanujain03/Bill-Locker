package project.bill_locker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import project.bill_locker.auth.ResetLinkSender;

/**
 * Replaces the log-writing sender in tests and remembers the last link per email,
 * so a test can "open the email" and use the link.
 */
public class RecordingResetLinkSender implements ResetLinkSender {

	private final Map<String, String> lastLinkByEmail = new ConcurrentHashMap<>();

	@Override
	public void send(String email, String resetLink) {
		lastLinkByEmail.put(email, resetLink);
	}

	/** The last link sent to this email, or null if none was sent. */
	public String lastLinkFor(String email) {
		return lastLinkByEmail.get(email);
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary // wins over LogResetLinkSender
		RecordingResetLinkSender recordingResetLinkSender() {
			return new RecordingResetLinkSender();
		}
	}
}
