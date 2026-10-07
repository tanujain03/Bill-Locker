package project.bill_locker.gmail;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import project.bill_locker.ApiTest;

/** Without a valid token key Gmail is "not configured": everything except GET answers 503. */
@TestPropertySource(properties = "app.gmail.token-key=")
class GmailNotConfiguredApiTests extends ApiTest {

	@Test
	void importIgnoreAndRestoreAnswer503() throws Exception {
		String token = registerAndGetToken(uniqueEmail("nogmail"));
		String body = "{\"fileIds\": [\"" + UUID.randomUUID() + "\"]}";

		for (String action : new String[] {"import", "ignore", "restore"}) {
			mvc.perform(post("/api/integrations/gmail/files/" + action).header("Authorization", bearer(token))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.code", is("GMAIL_NOT_CONFIGURED")));
		}
	}

	@Test
	void overviewStillAnswers() throws Exception {
		String token = registerAndGetToken(uniqueEmail("nogmail"));
		mvc.perform(get("/api/integrations/gmail").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.configured", is(false)));
	}
}
