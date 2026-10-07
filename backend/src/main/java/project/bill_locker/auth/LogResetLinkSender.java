package project.bill_locker.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no email account is set up (no MAIL_USERNAME, see {@link ResetLinkSenderConfig}):
 * writes the reset link to the backend log (IntelliJ's Run window). Copy it into the browser.
 */
class LogResetLinkSender implements ResetLinkSender {

	private static final Logger log = LoggerFactory.getLogger(LogResetLinkSender.class);

	@Override
	public void send(String email, String resetLink) {
		log.info("Password reset link for {} (works once): {}", email, resetLink);
	}
}
