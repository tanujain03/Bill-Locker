package project.bill_locker.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails the reset link through the SMTP server in {@code spring.mail.*} (e.g. Gmail).
 * The email has an HTML version (with a button) and a plain-text version.
 *
 * <p>If sending fails we only log why: the page must give the same answer as for an
 * unknown email, otherwise the error would reveal that the account exists.
 */
class EmailResetLinkSender implements ResetLinkSender {

	private static final Logger log = LoggerFactory.getLogger(EmailResetLinkSender.class);

	private final JavaMailSender mailSender;
	private final String fromAddress;
	private final String fromName;

	EmailResetLinkSender(JavaMailSender mailSender, String fromAddress, String fromName) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
		this.fromName = fromName;
	}

	@Override
	public void send(String email, String resetLink) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8"); // true = text + HTML parts
			helper.setFrom(fromAddress, fromName);
			helper.setTo(email);
			helper.setSubject("Reset your Bill Locker password");
			helper.setText(plainText(resetLink), html(resetLink));
			mailSender.send(message); // connects to the SMTP server, logs in, sends
			log.info("Password reset email sent to {}", email);
		}
		catch (MailAuthenticationException ex) {
			log.error("Could not send the reset email: the mail server rejected MAIL_USERNAME/MAIL_PASSWORD. "
					+ "For Gmail, MAIL_PASSWORD must be an App Password (Google account → Security → App passwords).", ex);
		}
		catch (MailException | MessagingException | UnsupportedEncodingException ex) {
			log.error("Could not send the reset email to {}", email, ex);
		}
	}

	private static String plainText(String resetLink) {
		return """
				Hi,

				you asked to reset the password of your Bill Locker account.
				Open this link to choose a new password:

				%s

				The link works once and expires in 30 minutes.
				If you didn't ask for this, ignore this email; your password stays the same.
				""".formatted(resetLink);
	}

	private static String html(String resetLink) {
		String link = HtmlUtils.htmlEscape(resetLink);
		return """
				<div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#0f172a">
				  <h2 style="color:#4f46e5">Bill Locker</h2>
				  <p>Someone (hopefully you) asked to reset the password of your Bill Locker account.</p>
				  <p style="margin:28px 0">
				    <a href="%s" style="background:#4f46e5;color:#ffffff;padding:12px 22px;border-radius:8px;text-decoration:none;font-weight:bold">
				      Choose a new password</a>
				  </p>
				  <p style="font-size:13px;color:#475569">The link works once and expires in 30 minutes.
				    If you didn't ask for this, ignore this email; your password stays the same.</p>
				  <p style="font-size:12px;color:#94a3b8">If the button doesn't work, copy this link:<br>%s</p>
				</div>
				""".formatted(link, link);
	}
}
