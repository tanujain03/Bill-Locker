package project.bill_locker.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses how reset links are delivered, once at start-up:
 * MAIL_USERNAME set → real email ({@link EmailResetLinkSender});
 * not set → written to the backend log ({@link LogResetLinkSender}).
 */
@Configuration
class ResetLinkSenderConfig {

	private static final Logger log = LoggerFactory.getLogger(ResetLinkSenderConfig.class);

	@Bean
	ResetLinkSender resetLinkSender(JavaMailSender mailSender,
			@Value("${spring.mail.host:}") String mailHost,
			@Value("${spring.mail.username:}") String mailUsername,
			@Value("${app.mail.from-name:Bill Locker}") String fromName) {
		if (mailUsername.isBlank()) {
			log.warn("MAIL_USERNAME is not set, so password reset links are only written to this log, "
					+ "not emailed. Set MAIL_USERNAME and MAIL_PASSWORD in backend/.env (see .env.example).");
			return new LogResetLinkSender();
		}
		log.info("Password reset links are emailed from {} via {}", mailUsername, mailHost);
		return new EmailResetLinkSender(mailSender, mailUsername, fromName);
	}
}
