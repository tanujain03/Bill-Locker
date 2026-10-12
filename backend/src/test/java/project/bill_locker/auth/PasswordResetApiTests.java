package project.bill_locker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.ApiTest;
import project.bill_locker.RecordingResetLinkSender;

/** Forgot password → link → reset password → sign in with the new password. */
class PasswordResetApiTests extends ApiTest {

	private static final String NEW_PASSWORD = "N3wPassword";

	@Autowired
	private RecordingResetLinkSender sentLinks;

	@Test
	void fullResetFlow() throws Exception {
		String email = uniqueEmail("forgot");
		registerAndGetToken(email);

		requestReset(email).andExpect(status().isOk()).andExpect(jsonPath("$.message").isNotEmpty());
		String link = sentLinks.lastLinkFor(email);
		assertThat(link).startsWith("http://localhost:5173/reset-password?token=");

		resetPassword(tokenFrom(link), NEW_PASSWORD).andExpect(status().isOk());

		postJson("/api/auth/login", loginJson(email, NEW_PASSWORD)).andExpect(status().isOk());
		postJson("/api/auth/login", loginJson(email, PASSWORD)).andExpect(status().isUnauthorized());
	}

	@Test
	void unknownEmailGetsTheSameAnswerButNoLink() throws Exception {
		String email = uniqueEmail("ghost");
		requestReset(email).andExpect(status().isOk()).andExpect(jsonPath("$.message").isNotEmpty());
		assertThat(sentLinks.lastLinkFor(email)).isNull();
	}

	@Test
	void linkWorksOnlyOnce() throws Exception {
		String email = uniqueEmail("once");
		registerAndGetToken(email);
		requestReset(email);
		String token = tokenFrom(sentLinks.lastLinkFor(email));

		resetPassword(token, NEW_PASSWORD).andExpect(status().isOk());
		resetPassword(token, "An0therPass")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
	}

	@Test
	void newRequestCancelsTheOlderLink() throws Exception {
		String email = uniqueEmail("twice");
		registerAndGetToken(email);
		requestReset(email);
		String oldToken = tokenFrom(sentLinks.lastLinkFor(email));
		requestReset(email);

		resetPassword(oldToken, NEW_PASSWORD).andExpect(status().isBadRequest());
		resetPassword(tokenFrom(sentLinks.lastLinkFor(email)), NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void madeUpTokenAndWeakPasswordAreRejected() throws Exception {
		resetPassword("made-up-token", NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
		resetPassword("made-up-token", "short")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	private ResultActions requestReset(String email) throws Exception {
		return postJson("/api/auth/forgot-password", """
				{"email": "%s"}
				""".formatted(email));
	}

	private ResultActions resetPassword(String token, String password) throws Exception {
		return postJson("/api/auth/reset-password", """
				{"token": "%s", "password": "%s"}
				""".formatted(token, password));
	}

	private static String tokenFrom(String link) {
		return link.substring(link.indexOf("token=") + "token=".length());
	}
}
