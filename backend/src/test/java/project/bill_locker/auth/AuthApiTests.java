package project.bill_locker.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import project.bill_locker.ApiTest;

/** Sign up, sign in and "who am I". */
class AuthApiTests extends ApiTest {

	@Test
	void registerReturnsTokenAndUserWithoutPassword() throws Exception {
		String email = uniqueEmail("asha");
		postJson("/api/auth/register", registerJson("Asha", email.toUpperCase(), PASSWORD))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.user.name").value("Asha"))
				.andExpect(jsonPath("$.user.email").value(email)) // saved lower-case
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist());
	}

	@Test
	void registerRejectsInvalidFields() throws Exception {
		postJson("/api/auth/register", registerJson("A", "not-an-email", "short"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.name").exists())
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	@Test
	void registerRejectsDuplicateEmail() throws Exception {
		String email = uniqueEmail("dup");
		registerAndGetToken(email);
		postJson("/api/auth/register", registerJson("Someone", email, PASSWORD))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void loginWorksWithRightPasswordOnly() throws Exception {
		String email = uniqueEmail("login");
		registerAndGetToken(email);

		postJson("/api/auth/login", loginJson(email, PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
		postJson("/api/auth/login", loginJson(email, "Wr0ngPass"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		// Unknown email: exactly the same answer as a wrong password.
		postJson("/api/auth/login", loginJson(uniqueEmail("nobody"), PASSWORD))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void meNeedsAValidToken() throws Exception {
		String email = uniqueEmail("me");
		String token = registerAndGetToken(email);

		mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
		mvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mvc.perform(get("/api/auth/me").header("Authorization", bearer(token + "x")))
				.andExpect(status().isUnauthorized());
	}
}
