package project.bill_locker.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import project.bill_locker.gmail.GoogleApi.GoogleApiException;

class GmailConnectApiTests extends GmailApiTestBase {

	private static final String GMAIL = "/api/integrations/gmail";

	@Autowired
	GmailAccountRepository accounts;

	@Autowired
	GmailConnectStateRepository states;

	@Test
	void connectGivesGoogleUrlWithStateAndPkce() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c1"));
		mvc.perform(post(GMAIL + "/connect").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authorizationUrl", containsString("state=")))
				.andExpect(jsonPath("$.authorizationUrl", containsString("code_challenge=")));
	}

	@Test
	void callbackConnectsAndRedirects() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c2"));
		String state = startConnect(token);
		google.willAuthorize("code-a", "a@gmail.com");

		assertThat(callback("code=code-a&state=" + state))
				.isEqualTo("http://localhost:5173/gmail?connected=a%40gmail.com");

		mvc.perform(get(GMAIL).header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.configured").value(true))
				.andExpect(jsonPath("$.accounts", hasSize(1)))
				.andExpect(jsonPath("$.accounts[0].email").value("a@gmail.com"))
				.andExpect(jsonPath("$.accounts[0].scanStatus").value("QUEUED"))
				.andExpect(jsonPath("$.counts.toReview").value(0))
				.andExpect(jsonPath("$.counts.ignored").value(0))
				.andExpect(jsonPath("$.counts.imported").value(0));
	}

	@Test
	void callbackNeedsNoLoginToken() throws Exception {
		// callback() sends no Authorization header and expects a redirect, not a 401
		String state = startConnect(registerAndGetToken(uniqueEmail("c3")));
		assertThat(callback("code=x&state=" + state)).startsWith("http://localhost:5173/gmail?error=");
	}

	@Test
	void overviewNeedsLogin() throws Exception {
		mvc.perform(get(GMAIL)).andExpect(status().isUnauthorized());
	}

	@Test
	void stateWorksOnce() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c4"));
		String state = startConnect(token);
		google.willAuthorize("code-a", "a@gmail.com");
		callback("code=code-a&state=" + state);

		assertThat(callback("code=code-a&state=" + state)).endsWith("?error=expired");
	}

	@Test
	void expiredStateIsRejected() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c5"));
		String state = startConnect(token);
		states.findAll().forEach(s -> {
			s.expireAt(Instant.now().minusSeconds(60));
			states.save(s);
		});
		google.willAuthorize("code-a", "a@gmail.com");

		assertThat(callback("code=code-a&state=" + state)).endsWith("?error=expired");
	}

	@Test
	void unknownStateIsRejected() throws Exception {
		assertThat(callback("code=code-a&state=nope")).endsWith("?error=expired");
	}

	@Test
	void googleDeniedAccess() throws Exception {
		String state = startConnect(registerAndGetToken(uniqueEmail("c6")));
		assertThat(callback("error=access_denied&state=" + state)).endsWith("?error=denied");
	}

	@Test
	void twoAccountsForOneUser() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c7"));
		connect(token, "a@gmail.com");
		connect(token, "b@gmail.com");

		mvc.perform(get(GMAIL).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.accounts", hasSize(2)));
	}

	@Test
	void sameAddressAgainUpdatesTheToken() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c8"));
		String first = connect(token, "a@gmail.com");
		String second = connect(token, "a@gmail.com");

		assertThat(second).isEqualTo(first);
		mvc.perform(get(GMAIL).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.accounts", hasSize(1)));
	}

	@Test
	void refreshTokenIsStoredEncrypted() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c9"));
		String id = connect(token, "enc@gmail.com");

		byte[] stored = accounts.findById(UUID.fromString(id)).orElseThrow().getRefreshTokenCiphertext();
		assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("refresh-enc@gmail.com");
		assertThat(stored.length).isGreaterThan("refresh-enc@gmail.com".length());
	}

	@Test
	void disconnectRevokesAndDeletes() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c10"));
		String id = connect(token, "a@gmail.com");

		mvc.perform(delete(GMAIL + "/accounts/" + id).header("Authorization", bearer(token)))
				.andExpect(status().isNoContent());

		mvc.perform(get(GMAIL).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.accounts", hasSize(0)));
		assertThat(google.revoked()).contains("refresh-a@gmail.com");
	}

	@Test
	void disconnectStillDeletesWhenGoogleRefusesTheRevoke() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c11"));
		String id = connect(token, "a@gmail.com");
		google.failNext(new GoogleApiException("already revoked", GoogleApiException.Kind.UNAVAILABLE, null));

		mvc.perform(delete(GMAIL + "/accounts/" + id).header("Authorization", bearer(token)))
				.andExpect(status().isNoContent());
		assertThat(accounts.findById(UUID.fromString(id))).isEmpty();
	}

	@Test
	void otherUsersAccountIs404() throws Exception {
		String owner = registerAndGetToken(uniqueEmail("c12"));
		String other = registerAndGetToken(uniqueEmail("c13"));
		String id = connect(owner, "a@gmail.com");

		mvc.perform(delete(GMAIL + "/accounts/" + id).header("Authorization", bearer(other)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GMAIL_ACCOUNT_NOT_FOUND"));
		assertThat(accounts.findById(UUID.fromString(id))).isPresent();
	}

	@Test
	void googleUnavailableOnCallback() throws Exception {
		String state = startConnect(registerAndGetToken(uniqueEmail("c14")));
		google.willAuthorize("code-a", "a@gmail.com");
		google.failNext(new GoogleApiException("down", GoogleApiException.Kind.UNAVAILABLE, null));

		assertThat(callback("code=code-a&state=" + state)).endsWith("?error=failed");
	}

	@Test
	void connectSetsAnHttpOnlyLaxCookie() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c15"));
		String header = mvc.perform(post(GMAIL + "/connect").header("Authorization", bearer(token)))
				.andReturn().getResponse().getHeader("Set-Cookie");
		assertThat(header).contains("gmail_connect=", "HttpOnly", "SameSite=Lax", "Path=/api/integrations/gmail",
				"Max-Age=600");
	}

	@Test
	void callbackClearsTheCookie() throws Exception {
		String state = startConnect(registerAndGetToken(uniqueEmail("c16")));
		google.willAuthorize("code-a", "a@gmail.com");
		String header = mvc.perform(get(GMAIL + "/callback?code=code-a&state=" + state).cookie(lastCookie))
				.andReturn().getResponse().getHeader("Set-Cookie");
		assertThat(header).contains("gmail_connect=", "Max-Age=0");
	}

	@Test
	void callbackWithoutTheCookieIsRejected() throws Exception {
		String token = registerAndGetToken(uniqueEmail("c17"));
		String state = startConnect(token);
		google.willAuthorize("code-a", "a@gmail.com");

		assertThat(callback("code=code-a&state=" + state, null)).endsWith("?error=expired");
		mvc.perform(get(GMAIL).header("Authorization", bearer(token))).andExpect(jsonPath("$.accounts", hasSize(0)));
	}

	@Test
	void callbackWithAnotherBrowsersCookieIsRejected() throws Exception {
		// the attacker starts a flow; the victim's browser (its own cookie) completes it
		String attackerToken = registerAndGetToken(uniqueEmail("c18"));
		String attackerState = startConnect(attackerToken);
		startConnect(registerAndGetToken(uniqueEmail("c19"))); // lastCookie is now another browser's
		google.willAuthorize("code-a", "a@gmail.com");

		assertThat(callback("code=code-a&state=" + attackerState)).endsWith("?error=expired");
		mvc.perform(get(GMAIL).header("Authorization", bearer(attackerToken)))
				.andExpect(jsonPath("$.accounts", hasSize(0)));
	}
}
