package project.bill_locker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import project.bill_locker.gmail.GmailAttachment;
import project.bill_locker.gmail.GoogleApi;

/**
 * A pretend Google for tests, so they never call the real one: every account is
 * "asha@gmail.com" with the same small inbox. {@code @Primary} makes Spring use it
 * instead of the real GoogleRestApi.
 */
@TestConfiguration
public class FakeGoogleApi {

	@Bean
	@Primary
	Fake fakeGoogleApi() {
		return new Fake();
	}

	public static class Fake implements GoogleApi {

		/** The one-time code Google would hand back after the user agreed. */
		public static final String GOOD_CODE = "good-code";
		public static final String REFRESH_TOKEN = "refresh-token-1";

		/** Refresh tokens given back to Google, for tests to check. */
		public final List<String> revoked = new CopyOnWriteArrayList<>();

		@Override
		public String authorizationUrl(String state, String codeChallenge) {
			return "https://accounts.google.com/o/oauth2/v2/auth?state=" + state + "&code_challenge=" + codeChallenge;
		}

		@Override
		public Tokens exchangeCode(String code, String codeVerifier) {
			if (!GOOD_CODE.equals(code)) {
				throw new GoogleApiException("invalid code", false, null);
			}
			return new Tokens("access-token-1", REFRESH_TOKEN);
		}

		@Override
		public String accessToken(String refreshToken) {
			if (!REFRESH_TOKEN.equals(refreshToken)) {
				throw new GoogleApiException("invalid_grant", true, null);
			}
			return "access-token-1";
		}

		@Override
		public String emailAddress(String accessToken) {
			return "asha@gmail.com";
		}

		@Override
		public List<String> searchMessages(String accessToken, String query, int max) {
			return List.of("m-invoice", "m-warranty", "m-newsletter", "m-no-file");
		}

		@Override
		public Email message(String accessToken, String messageId) {
			Instant now = Instant.now();
			return switch (messageId) {
				case "m-invoice" -> new Email(messageId, "\"Croma\" <orders@croma.com>", "Your Croma tax invoice",
						"Thank you for shopping with us", now.minus(Duration.ofDays(1)),
						List.of(new GmailAttachment("Invoice_408-123.pdf", "application/pdf", 900, "a-invoice")));
				case "m-warranty" -> new Email(messageId, "LG Electronics <care@lg.com>", "Warranty registration confirmed",
						"Your product is registered", now.minus(Duration.ofDays(2)),
						List.of(new GmailAttachment("warranty-card.png", "image/png", 5000, "a-card")));
				case "m-newsletter" -> new Email(messageId, "Deals <news@shop.example>", "Big sale ends tonight",
						"Unsubscribe any time", now.minus(Duration.ofDays(3)),
						List.of(new GmailAttachment("catalogue.pdf", "application/pdf", 900, "a-catalogue")));
				default -> new Email(messageId, "friend@example.com", "Dinner bill", "No file attached",
						now.minus(Duration.ofDays(4)), List.of());
			};
		}

		@Override
		public byte[] attachment(String accessToken, String messageId, String attachmentId) {
			return switch (attachmentId) {
				case "a-invoice" -> TestFiles.pdfWithText("TAX INVOICE", "Invoice No: CR/2026/123");
				case "a-card" -> TestFiles.pngWithText("WARRANTY CARD");
				default -> TestFiles.pdfWithText("Catalogue");
			};
		}

		@Override
		public void revoke(String refreshToken) {
			revoked.add(refreshToken);
		}
	}
}
