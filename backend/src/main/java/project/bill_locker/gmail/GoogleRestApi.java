package project.bill_locker.gmail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real Google calls (OAuth + Gmail REST), made directly with {@link RestClient}.
 * Not a component: {@link GmailConfig} builds it with timeouts; tests build it around a fake server.
 *
 * <p>Tokens and codes never go into log lines or exception messages, and response bodies
 * are never logged: only the HTTP status is.
 */
class GoogleRestApi implements GoogleApi {

	private static final Logger log = LoggerFactory.getLogger(GoogleRestApi.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
	private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
	private static final String GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me";
	private static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
	private static final int MAX_PAGE = 500; // Gmail's cap for maxResults

	private final RestClient restClient;
	private final GmailProperties properties;

	GoogleRestApi(RestClient.Builder builder, GmailProperties properties) {
		this.restClient = builder.build();
		this.properties = properties;
	}

	@Override
	public String authorizationUrl(String state, String codeChallenge) {
		// encode() before expanding, so every value (spaces, ':' and '/' in the scope) is escaped.
		return UriComponentsBuilder.fromUriString(AUTH_URL)
				.queryParam("client_id", "{clientId}")
				.queryParam("redirect_uri", "{redirectUri}")
				.queryParam("response_type", "code")
				.queryParam("scope", "{scope}")
				.queryParam("access_type", "offline") // offline = Google also gives a refresh token
				.queryParam("prompt", "{prompt}") // let the user pick which Google account to connect
				.queryParam("include_granted_scopes", "true")
				.queryParam("state", "{state}")
				.queryParam("code_challenge", "{challenge}")
				.queryParam("code_challenge_method", "S256")
				.encode()
				.buildAndExpand(properties.clientId(), properties.redirectUri(), SCOPE, "consent select_account",
						state, codeChallenge)
				.toUriString();
	}

	@Override
	public Tokens exchangeCode(String code, String codeVerifier) {
		MultiValueMap<String, String> form = clientForm("authorization_code");
		form.add("code", code);
		form.add("code_verifier", codeVerifier);
		form.add("redirect_uri", properties.redirectUri());
		JsonNode answer = tokenRequest(form);
		String refresh = answer.path("refresh_token").isString() ? answer.path("refresh_token").asString() : null;
		return new Tokens(requiredText(answer, "access_token"), refresh);
	}

	@Override
	public String accessToken(String refreshToken) {
		MultiValueMap<String, String> form = clientForm("refresh_token");
		form.add("refresh_token", refreshToken);
		return requiredText(tokenRequest(form), "access_token");
	}

	@Override
	public String emailAddress(String accessToken) {
		return requiredText(get(GMAIL + "/profile", accessToken), "emailAddress");
	}

	@Override
	public List<String> searchMessages(String accessToken, String query, int max) {
		List<String> ids = new ArrayList<>();
		String pageToken = null;
		while (ids.size() < max) {
			UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(GMAIL + "/messages")
					.queryParam("q", "{q}")
					.queryParam("maxResults", Math.min(MAX_PAGE, max - ids.size()));
			List<Object> values = new ArrayList<>(List.of(query));
			if (pageToken != null) {
				uri.queryParam("pageToken", "{pageToken}");
				values.add(pageToken);
			}
			JsonNode page = get(uri.encode().buildAndExpand(values.toArray()).toUriString(), accessToken);
			for (JsonNode message : page.path("messages")) {
				if (ids.size() < max && message.path("id").isString()) { // never return more than asked
					ids.add(message.path("id").asString());
				}
			}
			pageToken = page.path("nextPageToken").isString() ? page.path("nextPageToken").asString() : null;
			if (pageToken == null) {
				break;
			}
		}
		return ids;
	}

	@Override
	public Email message(String accessToken, String messageId) {
		JsonNode message = get(GMAIL + "/messages/" + segment(messageId) + "?format=full", accessToken);
		JsonNode payload = message.path("payload");
		List<Attachment> attachments = new ArrayList<>();
		collectAttachments(payload, attachments);
		long millis = parseLong(message.path("internalDate").asString(""));
		return new Email(messageId, header(payload, "From"), header(payload, "Subject"),
				message.path("snippet").asString(""), Instant.ofEpochMilli(millis), attachments);
	}

	@Override
	public byte[] attachment(String accessToken, String messageId, String attachmentId) {
		JsonNode answer = get(GMAIL + "/messages/" + segment(messageId) + "/attachments/" + segment(attachmentId),
				accessToken);
		try {
			// Gmail uses base64url (- and _), often without '=' padding; Java's decoder accepts both.
			return Base64.getUrlDecoder().decode(requiredText(answer, "data"));
		} catch (IllegalArgumentException e) {
			throw new GoogleApiException("Google sent an attachment that could not be decoded",
					GoogleApiException.Kind.UNAVAILABLE, e);
		}
	}

	@Override
	public void revoke(String refreshToken) {
		call(() -> restClient.post()
				.uri(REVOKE_URL + "?token={token}", refreshToken)
				.retrieve()
				.toBodilessEntity(), false);
	}

	// ---- Reading a message ---------------------------------------------------------

	/** Attachments hide at any depth of the MIME tree, so walk all parts. */
	private static void collectAttachments(JsonNode part, List<Attachment> into) {
		String attachmentId = part.path("body").path("attachmentId").asString("");
		String fileName = part.path("filename").asString("");
		// Big text bodies also have an attachmentId; only parts with a file name are files.
		if (!attachmentId.isEmpty() && !fileName.isBlank()) {
			String disposition = header(part, "Content-Disposition");
			boolean inline = disposition.toLowerCase().startsWith("inline");
			into.add(new Attachment(part.path("partId").asString(""), attachmentId, fileName,
					part.path("mimeType").asString(""), part.path("body").path("size").asLong(0), inline));
		}
		for (JsonNode child : part.path("parts")) {
			collectAttachments(child, into);
		}
	}

	private static String header(JsonNode part, String name) {
		for (JsonNode header : part.path("headers")) {
			if (name.equalsIgnoreCase(header.path("name").asString(""))) {
				return header.path("value").asString("");
			}
		}
		return "";
	}

	private static long parseLong(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// ---- HTTP ----------------------------------------------------------------------

	private MultiValueMap<String, String> clientForm(String grantType) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("grant_type", grantType);
		return form;
	}

	private JsonNode tokenRequest(MultiValueMap<String, String> form) {
		return read(call(() -> restClient.post()
				.uri(TOKEN_URL)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(String.class), true));
	}

	private JsonNode get(String url, String accessToken) {
		// Pre-built (already encoded) URL: pass it as a template-free string so it is not encoded twice.
		return read(call(() -> restClient.get()
				.uri(java.net.URI.create(url))
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.body(String.class), false));
	}

	/** Runs one request and turns every failure into a {@link GoogleApiException} without secrets. */
	private static <T> T call(Supplier<T> request, boolean tokenEndpoint) {
		try {
			return request.get();
		} catch (RestClientResponseException e) {
			int status = e.getStatusCode().value();
			log.warn("Google answered HTTP {}", status); // never the body or URL: they may hold tokens
			// Gmail also uses 403 for rate limits and a disabled API: those are not a withdrawn consent.
			if (status == 403 && transientForbidden(e.getResponseBodyAsString())) {
				throw new GoogleApiException("Google is limiting or not serving requests (HTTP 403)",
						GoogleApiException.Kind.UNAVAILABLE, null);
			}
			if (status == 401 || status == 403) {
				throw new GoogleApiException("Google refused access (HTTP " + status + ")",
						GoogleApiException.Kind.REVOKED, null);
			}
			// invalid_grant = the user withdrew access, the code was used twice, or the token expired.
			if (tokenEndpoint && status == 400 && e.getResponseBodyAsString().contains("invalid_grant")) {
				throw new GoogleApiException("Google no longer accepts this authorization",
						GoogleApiException.Kind.REVOKED, null);
			}
			if (status == 404) {
				throw new GoogleApiException("Google could not find it (HTTP 404)",
						GoogleApiException.Kind.NOT_FOUND, null);
			}
			throw new GoogleApiException("Google answered HTTP " + status, GoogleApiException.Kind.UNAVAILABLE, null);
		} catch (RestClientException e) { // no connection, timeout…
			log.warn("Could not reach Google: {}", e.getClass().getSimpleName());
			throw new GoogleApiException("Could not reach Google", GoogleApiException.Kind.UNAVAILABLE, null);
		}
	}

	/** True when a 403 body names a rate-limit or API-disabled reason; unparsable bodies are not. */
	private static boolean transientForbidden(String body) {
		try {
			JsonNode error = JSON.readTree(body == null ? "" : body).path("error");
			List<String> reasons = new ArrayList<>();
			for (JsonNode item : error.path("errors")) {
				reasons.add(item.path("reason").asString(""));
			}
			for (JsonNode item : error.path("details")) {
				reasons.add(item.path("reason").asString(""));
			}
			for (String reason : reasons) {
				if (List.of("rateLimitExceeded", "userRateLimitExceeded", "accessNotConfigured", "SERVICE_DISABLED")
						.contains(reason)) {
					log.warn("Google answered 403 with reason {}", reason);
					return true;
				}
			}
		} catch (JacksonException e) {
			// not JSON: treat as a real refusal
		}
		return false;
	}

	private static JsonNode read(String body) {
		try {
			return JSON.readTree(body == null ? "" : body);
		} catch (JacksonException e) {
			throw new GoogleApiException("Google sent an answer that could not be read",
					GoogleApiException.Kind.UNAVAILABLE, null);
		}
	}

	private static String requiredText(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isString() || value.asString().isEmpty()) {
			throw new GoogleApiException("Google's answer had no " + field, GoogleApiException.Kind.UNAVAILABLE, null);
		}
		return value.asString();
	}

	/** An id inside a path: ids are alphanumeric, but never trust that enough to skip escaping. */
	private static String segment(String id) {
		return java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
	}
}
