package project.bill_locker.gmail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The real {@link GoogleApi}: plain HTTPS calls to Google's OAuth 2 and Gmail REST
 * endpoints with Spring's {@link RestClient}. Tokens are never logged.
 */
@Component
class GoogleRestApi implements GoogleApi {

	private static final String AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String TOKEN = "https://oauth2.googleapis.com/token";
	private static final String REVOKE = "https://oauth2.googleapis.com/revoke";
	private static final String GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me";
	/** Read-only: Bill Locker can never send, change or delete anyone's mail. */
	static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

	private final GmailProperties properties;
	private final RestClient http = RestClient.create();

	GoogleRestApi(GmailProperties properties) {
		this.properties = properties;
	}

	@Override
	public String authorizationUrl(String state, String codeChallenge) {
		return UriComponentsBuilder.fromUriString(AUTHORIZE)
				.queryParam("client_id", properties.clientId())
				.queryParam("redirect_uri", properties.redirectUri())
				.queryParam("response_type", "code")
				.queryParam("scope", SCOPE)
				.queryParam("access_type", "offline") // also send a refresh token, for scans later on
				.queryParam("prompt", "consent") // ask every time, so Google sends that refresh token again
				.queryParam("state", state)
				.queryParam("code_challenge", codeChallenge) // PKCE, see GmailService.startConnect
				.queryParam("code_challenge_method", "S256")
				.encode()
				.toUriString();
	}

	@Override
	public Tokens exchangeCode(String code, String codeVerifier) {
		TokenResponse response = postForm(Map.of("grant_type", "authorization_code", "code", code,
				"code_verifier", codeVerifier, "redirect_uri", properties.redirectUri()));
		return new Tokens(response.accessToken(), response.refreshToken());
	}

	@Override
	public String accessToken(String refreshToken) {
		return postForm(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken)).accessToken();
	}

	@Override
	public String emailAddress(String accessToken) {
		return call(() -> http.get().uri(GMAIL + "/profile")
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve().body(Profile.class)).emailAddress();
	}

	@Override
	public List<String> searchMessages(String accessToken, String query, int max) {
		MessageList list = call(() -> http.get().uri(GMAIL + "/messages?q={query}&maxResults={max}", query, max)
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve().body(MessageList.class));
		return list == null || list.messages() == null ? List.of() : list.messages().stream().map(MessageRef::id).toList();
	}

	@Override
	public Email message(String accessToken, String messageId) {
		Message message = call(() -> http.get().uri(GMAIL + "/messages/{id}?format=full", messageId)
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve().body(Message.class));
		List<GmailAttachment> attachments = new ArrayList<>();
		collectAttachments(message.payload(), attachments);
		return new Email(message.id(), header(message.payload(), "from"), header(message.payload(), "subject"),
				message.snippet() == null ? "" : message.snippet(),
				Instant.ofEpochMilli(Long.parseLong(message.internalDate())), attachments);
	}

	@Override
	public byte[] attachment(String accessToken, String messageId, String attachmentId) {
		AttachmentBody body = call(() -> http.get().uri(GMAIL + "/messages/{m}/attachments/{a}", messageId, attachmentId)
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve().body(AttachmentBody.class));
		return Base64.getUrlDecoder().decode(body.data()); // Gmail sends file data as URL-safe Base64
	}

	@Override
	public void revoke(String refreshToken) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("token", refreshToken);
		call(() -> http.post().uri(REVOKE).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
				.retrieve().toBodilessEntity());
	}

	/** The token endpoint takes a form; the client id and secret prove this is Bill Locker. */
	private TokenResponse postForm(Map<String, String> fields) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		fields.forEach(form::add);
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		return call(() -> http.post().uri(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
				.retrieve().body(TokenResponse.class));
	}

	/** Turns any HTTP problem into a GoogleApiException; "invalid_grant" means our access was withdrawn. */
	private static <T> T call(Supplier<T> request) {
		try {
			return request.get();
		}
		catch (RestClientResponseException ex) {
			// Google's error answers hold an error code and a sentence, never a token: safe to log.
			String answer = ex.getResponseBodyAsString().replaceAll("\\s+", " ").strip();
			boolean revoked = answer.contains("invalid_grant");
			throw new GoogleApiException("Google answered " + ex.getStatusCode().value() + ": "
					+ (answer.length() > 300 ? answer.substring(0, 300) + "…" : answer), revoked, ex);
		}
		catch (RestClientException ex) {
			throw new GoogleApiException("Google could not be reached", false, ex);
		}
	}

	/** Attachments are message parts with a file name; they can be nested (e.g. inside "mixed" parts). */
	private static void collectAttachments(Part part, List<GmailAttachment> into) {
		if (part == null) {
			return;
		}
		if (part.filename() != null && !part.filename().isBlank() && part.body() != null && part.body().attachmentId() != null) {
			into.add(new GmailAttachment(part.filename(), part.mimeType(), part.body().size(), part.body().attachmentId()));
		}
		if (part.parts() != null) {
			part.parts().forEach(child -> collectAttachments(child, into));
		}
	}

	private static String header(Part payload, String name) {
		if (payload == null || payload.headers() == null) {
			return "";
		}
		return payload.headers().stream()
				.filter(header -> header.name().toLowerCase(Locale.ROOT).equals(name))
				.map(Header::value)
				.findFirst()
				.orElse("");
	}

	// ---- The JSON Google sends (only the fields we use) ---------------------------------

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TokenResponse(@JsonProperty("access_token") String accessToken,
			@JsonProperty("refresh_token") String refreshToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Profile(String emailAddress) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MessageList(List<MessageRef> messages) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MessageRef(String id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Message(String id, String snippet, String internalDate, Part payload) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Part(String mimeType, String filename, List<Header> headers, PartBody body, List<Part> parts) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Header(String name, String value) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record PartBody(String attachmentId, long size) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record AttachmentBody(String data) {
	}
}
