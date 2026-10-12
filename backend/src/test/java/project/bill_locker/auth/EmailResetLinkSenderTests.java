package project.bill_locker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Sends the reset email over real SMTP to GreenMail, a small mail server that runs
 * inside the test (no Docker, no internet), then reads what arrived.
 */
class EmailResetLinkSenderTests {

	private static final String FROM = "bills@example.com";
	private static final String PASSWORD = "app-password";
	private static final String LINK = "http://localhost:5173/reset-password?token=abc123";

	@RegisterExtension
	static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP)
			.withConfiguration(GreenMailConfiguration.aConfig().withUser(FROM, PASSWORD));

	@Test
	void emailsTheLinkWithTextAndButton() throws Exception {
		new EmailResetLinkSender(mailSender(smtp.getSmtp().getPort()), FROM, "Bill Locker").send("asha@example.com", LINK);

		MimeMessage[] received = smtp.getReceivedMessages();
		assertThat(received).hasSize(1);
		MimeMessage email = received[0];
		assertThat(email.getSubject()).isEqualTo("Reset your Bill Locker password");
		assertThat(email.getFrom()[0].toString()).isEqualTo("Bill Locker <" + FROM + ">");
		assertThat(email.getAllRecipients()[0].toString()).isEqualTo("asha@example.com");
		String content = allText(email);
		assertThat(content).contains(LINK);                              // plain-text part
		assertThat(content).contains("<a href=\"" + LINK + "\"");      // HTML button
	}

	@Test
	void serverProblemsAreLoggedNotThrown() {
		// Nothing listens on this port: sending fails, but the request must still get the normal answer.
		EmailResetLinkSender sender = new EmailResetLinkSender(mailSender(1), FROM, "Bill Locker");
		assertThatCode(() -> sender.send("asha@example.com", LINK)).doesNotThrowAnyException();
	}

	private static JavaMailSenderImpl mailSender(int port) {
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost("localhost");
		mailSender.setPort(port);
		mailSender.setUsername(FROM);
		mailSender.setPassword(PASSWORD);
		mailSender.getJavaMailProperties().put("mail.smtp.auth", "true"); // log in, like Gmail needs
		mailSender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
		return mailSender;
	}

	/** All text of an email, from every part (plain text and HTML). */
	private static String allText(Part part) throws Exception {
		Object content = part.getContent();
		if (content instanceof String text) {
			return text;
		}
		StringBuilder all = new StringBuilder();
		if (content instanceof Multipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				BodyPart child = multipart.getBodyPart(i);
				all.append(allText(child)).append('\n');
			}
		}
		return all.toString();
	}
}
