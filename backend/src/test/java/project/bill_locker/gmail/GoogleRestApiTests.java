package project.bill_locker.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import project.bill_locker.gmail.GoogleApi.Attachment;
import project.bill_locker.gmail.GoogleApi.Email;
import project.bill_locker.gmail.GoogleApi.GoogleApiException;
import project.bill_locker.gmail.GoogleApi.Tokens;

/** The HTTP calls to Google, against a fake server (nothing leaves this computer). */
class GoogleRestApiTests {

	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
	private static final String GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me";
	private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="; // 32 bytes of zeros

	private MockRestServiceServer google;
	private GoogleRestApi api;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		google = MockRestServiceServer.bindTo(builder).build();
		api = new GoogleRestApi(builder, new GmailProperties("test-client", "test-secret", KEY,
				"http://localhost:8080/api/integrations/gmail/callback"));
	}

	private static GoogleApiException.Kind kindOf(Runnable call) {
		try {
			call.run();
		} catch (GoogleApiException e) {
			return e.kind();
		}
		return null;
	}

	@Test
	void authorizationUrlAsksForReadOnlyAndAccountChoice() {
		URI url = URI.create(api.authorizationUrl("st1", "chal1"));
		var query = UriComponentsBuilder.fromUri(url).build().getQueryParams();

		assertThat(url.getHost()).isEqualTo("accounts.google.com");
		assertThat(url.getPath()).isEqualTo("/o/oauth2/v2/auth");
		assertThat(url.getRawQuery()).contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fgmail.readonly");
		// These are the values Google sees after decoding.
		assertThat(decoded(url, "scope")).isEqualTo("https://www.googleapis.com/auth/gmail.readonly");
		assertThat(decoded(url, "prompt")).isEqualTo("consent select_account");
		assertThat(decoded(url, "redirect_uri")).isEqualTo("http://localhost:8080/api/integrations/gmail/callback");
		assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
		assertThat(query.getFirst("code_challenge")).isEqualTo("chal1");
		assertThat(query.getFirst("access_type")).isEqualTo("offline");
		assertThat(query.getFirst("response_type")).isEqualTo("code");
		assertThat(query.getFirst("client_id")).isEqualTo("test-client");
		assertThat(query.getFirst("state")).isEqualTo("st1");
		assertThat(query.getFirst("include_granted_scopes")).isEqualTo("true");
	}

	private static String decoded(URI url, String name) {
		for (String pair : url.getQuery().split("&")) {
			if (pair.startsWith(name + "=")) {
				return pair.substring(name.length() + 1);
			}
		}
		return null;
	}

	@Test
	void exchangeCodeSendsVerifier() {
		google.expect(requestTo(TOKEN_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
				.andExpect(content().string(containsString("code_verifier=v1")))
				.andExpect(content().string(containsString("grant_type=authorization_code")))
				.andExpect(content().string(containsString("code=c1")))
				.andExpect(content().string(containsString("client_id=test-client")))
				.andExpect(content().string(containsString("client_secret=test-secret")))
				.andExpect(content().string(containsString("redirect_uri=")))
				.andRespond(withSuccess("{\"access_token\":\"a\",\"refresh_token\":\"r\"}", MediaType.APPLICATION_JSON));

		assertThat(api.exchangeCode("c1", "v1")).isEqualTo(new Tokens("a", "r"));
		google.verify();
	}

	@Test
	void exchangeCodeWithoutRefreshTokenGivesNull() {
		google.expect(requestTo(TOKEN_URL))
				.andRespond(withSuccess("{\"access_token\":\"a\"}", MediaType.APPLICATION_JSON));

		assertThat(api.exchangeCode("c1", "v1")).isEqualTo(new Tokens("a", null));
	}

	@Test
	void accessTokenUsesRefreshToken() {
		google.expect(requestTo(TOKEN_URL))
				.andExpect(content().string(containsString("grant_type=refresh_token")))
				.andExpect(content().string(containsString("refresh_token=r1")))
				.andRespond(withSuccess("{\"access_token\":\"fresh\"}", MediaType.APPLICATION_JSON));

		assertThat(api.accessToken("r1")).isEqualTo("fresh");
	}

	@Test
	void invalidGrantMeansRevoked() {
		google.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\"}"));

		assertThatThrownBy(() -> api.accessToken("r1"))
				.isInstanceOfSatisfying(GoogleApiException.class,
						e -> assertThat(e.kind()).isEqualTo(GoogleApiException.Kind.REVOKED))
				.hasMessageNotContaining("r1");
	}

	@Test
	void otherTokenEndpointBadRequestIsUnavailable() {
		google.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_request\"}"));

		assertThat(kindOf(() -> api.accessToken("r1"))).isEqualTo(GoogleApiException.Kind.UNAVAILABLE);
	}

	@Test
	void unauthorizedIsRevoked() {
		google.expect(requestTo(GMAIL + "/profile")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThat(kindOf(() -> api.emailAddress("tok"))).isEqualTo(GoogleApiException.Kind.REVOKED);
	}

	private GoogleApiException.Kind kindOfForbidden(String reason) {
		google.expect(requestTo(GMAIL + "/profile")).andRespond(withStatus(HttpStatus.FORBIDDEN)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":{\"code\":403,\"errors\":[{\"reason\":\"" + reason + "\"}]}}"));
		return kindOf(() -> api.emailAddress("tok"));
	}

	@Test
	void rateLimitForbiddenIsUnavailable() {
		assertThat(kindOfForbidden("rateLimitExceeded")).isEqualTo(GoogleApiException.Kind.UNAVAILABLE);
	}

	@Test
	void apiNotEnabledForbiddenIsUnavailable() {
		assertThat(kindOfForbidden("accessNotConfigured")).isEqualTo(GoogleApiException.Kind.UNAVAILABLE);
	}

	@Test
	void otherForbiddenIsRevoked() {
		assertThat(kindOfForbidden("insufficientPermissions")).isEqualTo(GoogleApiException.Kind.REVOKED);
	}

	@Test
	void emailAddressReadsProfile() {
		google.expect(requestTo(GMAIL + "/profile"))
				.andExpect(header("Authorization", "Bearer tok"))
				.andRespond(withSuccess("{\"emailAddress\":\"me@gmail.com\"}", MediaType.APPLICATION_JSON));

		assertThat(api.emailAddress("tok")).isEqualTo("me@gmail.com");
	}

	@Test
	void searchFollowsPages() {
		google.expect(requestTo(startsWith(GMAIL + "/messages?")))
				.andExpect(requestTo(containsString("q=has%3Aattachment%20newer_than%3A1y")))
				.andExpect(requestTo(containsString("maxResults=10")))
				.andRespond(withSuccess("{\"messages\":[{\"id\":\"1\"},{\"id\":\"2\"}],\"nextPageToken\":\"p2\"}",
						MediaType.APPLICATION_JSON));
		google.expect(requestTo(containsString("pageToken=p2")))
				.andExpect(requestTo(containsString("maxResults=8")))
				.andRespond(withSuccess("{\"messages\":[{\"id\":\"3\"}]}", MediaType.APPLICATION_JSON));

		assertThat(api.searchMessages("tok", "has:attachment newer_than:1y", 10)).containsExactly("1", "2", "3");
		google.verify();
	}

	@Test
	void searchStopsAtMax() {
		google.expect(requestTo(startsWith(GMAIL + "/messages?")))
				.andRespond(withSuccess("{\"messages\":[{\"id\":\"1\"},{\"id\":\"2\"}],\"nextPageToken\":\"p2\"}",
						MediaType.APPLICATION_JSON));

		assertThat(api.searchMessages("tok", "x", 2)).containsExactly("1", "2");
		google.verify(); // a second request would have failed: none was expected
	}

	@Test
	void searchWithNoMatchesIsEmpty() {
		google.expect(requestTo(startsWith(GMAIL + "/messages?")))
				.andRespond(withSuccess("{\"resultSizeEstimate\":0}", MediaType.APPLICATION_JSON));

		assertThat(api.searchMessages("tok", "x", 5)).isEmpty();
	}

	@Test
	void messageReadsHeadersAndNestedAttachments() {
		String json = """
				{"id":"m1","snippet":"Your invoice","internalDate":"1700000000000",
				 "payload":{"mimeType":"multipart/mixed","headers":[
				   {"name":"From","value":"Shop <shop@example.com>"},
				   {"name":"subject","value":"Invoice 42"}],
				  "parts":[
				   {"partId":"0","mimeType":"multipart/alternative","filename":"","body":{"size":0},"parts":[
				     {"partId":"0.0","mimeType":"text/plain","filename":"","body":{"size":10,"data":"aGk"}}]},
				   {"partId":"1","mimeType":"application/pdf","filename":"Invoice.pdf",
				    "headers":[{"name":"Content-Disposition","value":"attachment; filename=Invoice.pdf"}],
				    "body":{"attachmentId":"att1","size":1234}},
				   {"partId":"2","mimeType":"image/png","filename":"logo.png",
				    "headers":[{"name":"Content-Disposition","value":"inline; filename=logo.png"}],
				    "body":{"attachmentId":"att2","size":99}}]}}
				""";
		google.expect(requestTo(GMAIL + "/messages/m1?format=full"))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		Email email = api.message("tok", "m1");

		assertThat(email.id()).isEqualTo("m1");
		assertThat(email.from()).isEqualTo("Shop <shop@example.com>");
		assertThat(email.subject()).isEqualTo("Invoice 42");
		assertThat(email.snippet()).isEqualTo("Your invoice");
		assertThat(email.receivedAt()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
		assertThat(email.attachments()).containsExactly(
				new Attachment("1", "att1", "Invoice.pdf", "application/pdf", 1234, false),
				new Attachment("2", "att2", "logo.png", "image/png", 99, true));
	}

	@Test
	void partWithoutFileNameIsNotAnAttachment() {
		String json = """
				{"id":"m1","internalDate":"1","payload":{"mimeType":"multipart/mixed","headers":[],"parts":[
				  {"partId":"1","mimeType":"text/html","filename":"","body":{"attachmentId":"big","size":50000}}]}}
				""";
		google.expect(requestTo(GMAIL + "/messages/m1?format=full"))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		assertThat(api.message("tok", "m1").attachments()).isEmpty();
	}

	@Test
	void attachmentDecodesBase64Url() {
		google.expect(requestTo(GMAIL + "/messages/m1/attachments/att1"))
				.andRespond(withSuccess("{\"size\":6,\"data\":\"JVBERi0x\"}", MediaType.APPLICATION_JSON));

		assertThat(api.attachment("tok", "m1", "att1")).isEqualTo("%PDF-1".getBytes(StandardCharsets.US_ASCII));
	}

	@Test
	void revokePostsTheToken() {
		google.expect(requestTo("https://oauth2.googleapis.com/revoke?token=r1"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess());

		api.revoke("r1");
		google.verify();
	}

	@Test
	void notFoundMessage() {
		google.expect(requestTo(GMAIL + "/messages/gone?format=full")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(kindOf(() -> api.message("tok", "gone"))).isEqualTo(GoogleApiException.Kind.NOT_FOUND);
	}

	@Test
	void serverErrorIsUnavailable() {
		google.expect(requestTo(GMAIL + "/profile")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThat(kindOf(() -> api.emailAddress("tok"))).isEqualTo(GoogleApiException.Kind.UNAVAILABLE);
	}

	@Test
	void noConnectionIsUnavailable() {
		google.expect(requestTo(GMAIL + "/profile")).andRespond(request -> {
			throw new java.net.SocketTimeoutException("read timed out");
		});

		assertThat(kindOf(() -> api.emailAddress("tok"))).isEqualTo(GoogleApiException.Kind.UNAVAILABLE);
	}

	@Test
	void garbageAnswerIsUnavailable() {
		google.expect(requestTo(GMAIL + "/profile")).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));

		assertThat(kindOf(() -> api.emailAddress("tok"))).isEqualTo(GoogleApiException.Kind.UNAVAILABLE);
	}
}
