package project.bill_locker.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import project.bill_locker.document.DocumentType;

/** The token encryption and the email rules. Plain Java: no database or Google needed. */
class GmailRulesTests {

	private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // 32 bytes in Base64

	private final TokenCipher cipher = new TokenCipher(new GmailProperties(null, null, KEY, null, null, false));
	private final EmailClassifier classifier = new EmailClassifier();

	@Test
	void theRefreshTokenIsStoredEncrypted() {
		byte[] once = cipher.encrypt("refresh-token");
		byte[] twice = cipher.encrypt("refresh-token");

		assertThat(once).isNotEqualTo(twice); // a fresh random nonce every time
		assertThat(new String(once)).doesNotContain("refresh-token");
		assertThat(cipher.decrypt(once)).isEqualTo("refresh-token");

		once[once.length - 1] ^= 1; // one changed bit…
		assertThatThrownBy(() -> cipher.decrypt(once)).isInstanceOf(IllegalStateException.class); // …is noticed
	}

	@Test
	void theSetupSaysWhichSettingIsMissingOrWrong() {
		assertThat(new GmailProperties("id", "secret", null, null, null, true).problems())
				.containsExactly("GMAIL_TOKEN_KEY is not set");
		assertThat(new GmailProperties("id", "secret", "c2hvcnQ=", null, null, true).problems())
				.containsExactly("GMAIL_TOKEN_KEY must be 32 random bytes in Base64 (44 characters)");
		assertThat(new GmailProperties("id", "secret", KEY, null, null, true).isConfigured()).isTrue();
	}

	@Test
	void googlesErrorsAreExplainedInWordsToActOn() {
		assertThat(GmailService.explain(new GoogleApi.GoogleApiException(
				"Google answered 403: {\"error\": {\"message\": \"Gmail API has not been used in project 123 before or it is "
						+ "disabled.\", \"status\": \"PERMISSION_DENIED\", \"reason\": \"SERVICE_DISABLED\"}}", false, null)))
				.startsWith("The Gmail API is not enabled");
		assertThat(GmailService.explain(new GoogleApi.GoogleApiException(
				"Google answered 401: {\"error\": \"invalid_client\"}", false, null)))
				.contains("client ID or secret");
	}

	@Test
	void emailsAreSortedByHowLikelyTheyHoldABill() {
		assertThat(verdict("Your tax invoice", "Invoice_123.pdf"))
				.isEqualTo(new EmailClassifier.Verdict(DocumentType.INVOICE, 0.95));
		assertThat(verdict("Warranty card for your LG AC", "card.png").type()).isEqualTo(DocumentType.WARRANTY_CARD);
		assertThat(verdict("Repair completed: job card 42", "jobcard.pdf").type()).isEqualTo(DocumentType.REPAIR_RECEIPT);
		assertThat(verdict("Your order has shipped", "details.pdf").confidence()).isEqualTo(0.65);
		assertThat(verdict("Your credit card statement", "statement.pdf").confidence()).isEqualTo(0.15);
		assertThat(verdict("Billion-dollar ideas", "slides.pdf").type()).as("whole words only").isEqualTo(DocumentType.OTHER);
	}

	@Test
	void onlyPdfsAndPhotosUpTo10MbCanBeImported() {
		assertThat(GmailService.importable(new GmailAttachment("Invoice.PDF", "application/pdf", 1000, "a"))).isTrue();
		assertThat(GmailService.importable(new GmailAttachment("photo.jpeg", "image/jpeg", 1000, "a"))).isTrue();
		assertThat(GmailService.importable(new GmailAttachment("invite.ics", "text/calendar", 1000, "a"))).isFalse();
		assertThat(GmailService.importable(new GmailAttachment("huge.pdf", "application/pdf", 11L * 1024 * 1024, "a"))).isFalse();
	}

	private EmailClassifier.Verdict verdict(String subject, String fileName) {
		return classifier.classify(subject, "", List.of(new GmailAttachment(fileName, "application/pdf", 1000, "a")));
	}
}
