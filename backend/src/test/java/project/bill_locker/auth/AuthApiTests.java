package project.bill_locker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import project.bill_locker.ApiTest;
import project.bill_locker.user.User;
import project.bill_locker.user.UserRepository;

/** Registration, login and the token check. */
class AuthApiTests extends ApiTest {

	@Autowired
	private UserRepository users;

	@Test
	void registeringCreatesTheAccountAndReturnsAToken() throws Exception {
		String email = uniqueEmail("Asha.Verma").replace("example.com", "Example.COM");

		mvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("Asha Verma", email, PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresAt").isString())
				.andExpect(jsonPath("$.user.id").isNotEmpty())
				.andExpect(jsonPath("$.user.name").value("Asha Verma"))
				.andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
				.andExpect(jsonPath("$.user.createdAt").isString())
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist());
	}

	@Test
	void passwordsAreStoredAsBcryptHashes() throws Exception {
		String email = uniqueEmail("hash");
		registerAndGetToken(email);

		User saved = users.findByEmail(email).orElseThrow();
		assertThat(saved.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
	}

	@Test
	void anEmailCanOnlyRegisterOnce() throws Exception {
		String email = uniqueEmail("twice");
		registerAndGetToken(email);

		mvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("Someone Else", email.toUpperCase(), "An0therPass")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
	}

	@Test
	void registrationChecksEveryField() throws Exception {
		mvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("A", "not-an-email", "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
				.andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
				.andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
	}

	@Test
	void loginAcceptsOnlyTheRightPassword() throws Exception {
		String email = uniqueEmail("login");
		registerAndGetToken(email);

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(email, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.user.email").value(email));

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(email, "Wr0ngPass")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Incorrect email or password."));

		// An unknown email gets exactly the same answer as a wrong password.
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(uniqueEmail("nobody"), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Incorrect email or password."));
	}

	@Test
	void meNeedsAValidToken() throws Exception {
		String token = registerAndGetToken(uniqueEmail("me"));

		mvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer("not.a.token")))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Test User"));
	}

	@Test
	void aTokenCannotBeAltered() throws Exception {
		String ashasToken = registerAndGetToken(uniqueEmail("asha"));
		String bobsToken = registerAndGetToken(uniqueEmail("bob"));
		// Asha's header and claims with the signature of Bob's token: the signature no longer matches.
		String forged = ashasToken.substring(0, ashasToken.lastIndexOf('.'))
				+ bobsToken.substring(bobsToken.lastIndexOf('.'));

		mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(forged)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anOldTokenInTheBrowserDoesNotBlockLogin() throws Exception {
		String email = uniqueEmail("stale");
		registerAndGetToken(email);

		mvc.perform(post("/api/auth/login")
						.header(AUTHORIZATION, bearer("an.expired.token"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(email, PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	void usersCanChangeTheirName() throws Exception {
		String token = registerAndGetToken(uniqueEmail("rename"));

		mvc.perform(put("/api/users/me")
						.header(AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"  New Name  \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New Name"));
		mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.name").value("New Name"));
	}
}
