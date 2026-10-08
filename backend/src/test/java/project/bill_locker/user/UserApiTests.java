package project.bill_locker.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.ApiTest;

/** Settings: change your name, change your password. */
class UserApiTests extends ApiTest {

	@Test
	void renameChangesTheNameEverywhere() throws Exception {
		String token = registerAndGetToken(uniqueEmail("rename"));

		putJson("/api/users/me", token, """
				{"name": "  Tanu Jain  "}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Tanu Jain")); // trimmed
		mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.name").value("Tanu Jain"));
	}

	@Test
	void renameFollowsTheSignUpRules() throws Exception {
		String token = registerAndGetToken(uniqueEmail("badname"));
		putJson("/api/users/me", token, """
				{"name": "A"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());
	}

	@Test
	void changePasswordNeedsTheCurrentOne() throws Exception {
		String email = uniqueEmail("pw");
		String token = registerAndGetToken(email);

		// Wrong current password: 400 (not 401, which would sign the browser out).
		putJson("/api/users/me/password", token, passwordJson("Wr0ngPass", "N3wPassword"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WRONG_PASSWORD"))
				.andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
		// A weak new password breaks the sign-up rules.
		putJson("/api/users/me/password", token, passwordJson(PASSWORD, "short"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.newPassword").exists());

		putJson("/api/users/me/password", token, passwordJson(PASSWORD, "N3wPassword"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Your password has been changed."));

		postJson("/api/auth/login", loginJson(email, "N3wPassword")).andExpect(status().isOk());
		postJson("/api/auth/login", loginJson(email, PASSWORD)).andExpect(status().isUnauthorized());
	}

	@Test
	void needsASignedInUser() throws Exception {
		mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Someone\"}"))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions putJson(String url, String token, String json) throws Exception {
		return mvc.perform(put(url).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String passwordJson(String current, String next) {
		return """
				{"currentPassword": "%s", "newPassword": "%s"}
				""".formatted(current, next);
	}
}
