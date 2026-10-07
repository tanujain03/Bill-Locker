package project.bill_locker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import project.bill_locker.gmail.GoogleApi;

/**
 * Stands in for Google in tests: no network, no sign-in. A test registers the codes
 * Google would accept and the emails a mailbox holds, and can make the next call fail.
 */
public class FakeGoogleApi implements GoogleApi {

	// address -> emails in the order they were added
	private final Map<String, List<Email>> mailboxes = new LinkedHashMap<>();
	private final Map<String, Map<String, byte[]>> files = new HashMap<>(); // address -> attachmentId -> bytes
	private final Map<String, String> codes = new HashMap<>(); // code -> address
	private final List<String> revoked = new ArrayList<>();
	private String lastQuery;
	private GoogleApiException failure;

	@Override
	public String authorizationUrl(String state, String codeChallenge) {
		failIfAsked();
		return "https://fake.google/auth?state=" + state + "&code_challenge=" + codeChallenge;
	}

	public void willAuthorize(String code, String address) {
		codes.put(code, address);
	}

	@Override
	public Tokens exchangeCode(String code, String codeVerifier) {
		failIfAsked();
		String address = codes.get(code);
		if (address == null) {
			throw new GoogleApiException("Unknown code", GoogleApiException.Kind.REVOKED, null);
		}
		return new Tokens("access-" + address, "refresh-" + address);
	}

	@Override
	public String accessToken(String refreshToken) {
		failIfAsked();
		return "access-" + refreshToken.substring("refresh-".length());
	}

	@Override
	public String emailAddress(String accessToken) {
		failIfAsked();
		return accessToken.substring("access-".length());
	}

	public void addEmail(String address, Email email, Map<String, byte[]> filesByAttachmentId) {
		mailboxes.computeIfAbsent(address, a -> new ArrayList<>()).add(email);
		files.computeIfAbsent(address, a -> new HashMap<>()).putAll(filesByAttachmentId);
	}

	public void removeEmail(String address, String id) {
		mailboxes.getOrDefault(address, new ArrayList<>()).removeIf(email -> email.id().equals(id));
	}

	@Override
	public List<String> searchMessages(String accessToken, String query, int max) {
		failIfAsked();
		lastQuery = query;
		return mailbox(accessToken).stream()
				.sorted(Comparator.comparing(Email::receivedAt).reversed())
				.limit(max)
				.map(Email::id)
				.toList();
	}

	@Override
	public Email message(String accessToken, String messageId) {
		failIfAsked();
		return find(accessToken, messageId);
	}

	@Override
	public byte[] attachment(String accessToken, String messageId, String attachmentId) {
		failIfAsked();
		Email email = find(accessToken, messageId);
		byte[] bytes = files.getOrDefault(accessToken.substring("access-".length()), Map.of()).get(attachmentId);
		if (bytes == null || email.attachments().stream().noneMatch(a -> attachmentId.equals(a.attachmentId()))) {
			throw new GoogleApiException("No such attachment", GoogleApiException.Kind.NOT_FOUND, null);
		}
		return bytes;
	}

	@Override
	public void revoke(String refreshToken) {
		failIfAsked();
		revoked.add(refreshToken);
	}

	/** The next call of any method throws this, then things work again. */
	public void failNext(GoogleApiException failure) {
		this.failure = failure;
	}

	public List<String> revoked() {
		return revoked;
	}

	public String lastQuery() {
		return lastQuery;
	}

	public void reset() {
		mailboxes.clear();
		files.clear();
		codes.clear();
		revoked.clear();
		lastQuery = null;
		failure = null;
	}

	private Email find(String accessToken, String messageId) {
		return mailbox(accessToken).stream().filter(email -> email.id().equals(messageId)).findFirst()
				.orElseThrow(() -> new GoogleApiException("No such email", GoogleApiException.Kind.NOT_FOUND, null));
	}

	private List<Email> mailbox(String accessToken) {
		return mailboxes.getOrDefault(accessToken.substring("access-".length()), List.of());
	}

	private void failIfAsked() {
		if (failure != null) {
			GoogleApiException toThrow = failure;
			failure = null;
			throw toThrow;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		FakeGoogleApi fakeGoogleApi() {
			return new FakeGoogleApi();
		}
	}
}
