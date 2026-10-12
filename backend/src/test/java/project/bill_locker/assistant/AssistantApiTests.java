package project.bill_locker.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.FakeAssistantModel;
import project.bill_locker.document.DocumentApiTestBase;

/** POST /api/assistant/chat with a fake AI: the real query runner and database do the rest. */
class AssistantApiTests extends DocumentApiTestBase {

	private static final String QUESTION = "[{\"role\": \"user\", \"text\": \"Where did I shop?\"}]";
	private static final String CANT_LOOK_UP = "I couldn't look that up. Try asking in a different way.";

	@Autowired
	FakeAssistantModel fake;

	String tokenA;

	@BeforeEach
	void twoUsersWithOneBillEach() throws Exception {
		fake.reset();
		tokenA = registerAndGetToken(uniqueEmail("a"));
		saveBill(tokenA, bill("Croma"));
		saveBill(registerAndGetToken(uniqueEmail("b")), bill("Reliance"));
	}

	@Test
	void queryPlanRunsSqlAndAnswersFromOwnRows() throws Exception {
		fake.queuePlan(new Plan(Plan.Action.QUERY, "SELECT seller_name FROM ai_documents", null));

		chat(tokenA, QUESTION)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("rows=[{seller_name=Croma}]"))
				.andExpect(jsonPath("$.sql").value("SELECT seller_name FROM ai_documents"));
	}

	@Test
	void replyPlanRunsNoQuery() throws Exception {
		fake.queuePlan(new Plan(Plan.Action.REPLY, null, "I can only look things up."));

		chat(tokenA, QUESTION)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("I can only look things up."))
				.andExpect(jsonPath("$.sql").doesNotExist());
	}

	@Test
	void badSqlIsRetriedOnceWithTheError() throws Exception {
		fake.queuePlan(new Plan(Plan.Action.QUERY, "SELECT nope FROM ai_documents", null));
		fake.queuePlan(new Plan(Plan.Action.QUERY, "SELECT count(*) AS n FROM ai_documents", null));

		chat(tokenA, QUESTION)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("rows=[{n=1}]"));

		assertThat(fake.planCalls()).hasSize(2);
		assertThat(fake.planCalls().get(0).failedSql()).isNull();
		assertThat(fake.planCalls().get(1).failedSql()).isEqualTo("SELECT nope FROM ai_documents");
		assertThat(fake.planCalls().get(1).error()).contains("nope");
	}

	@Test
	void failingTwiceGivesFriendlyAnswer() throws Exception {
		fake.queuePlan(new Plan(Plan.Action.QUERY, "SELECT nope FROM ai_documents", null));
		fake.queuePlan(new Plan(Plan.Action.QUERY, "SELECT still_nope FROM ai_documents", null));

		chat(tokenA, QUESTION)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value(CANT_LOOK_UP))
				.andExpect(jsonPath("$.sql").value("SELECT still_nope FROM ai_documents"));
		assertThat(fake.planCalls()).hasSize(2);
	}

	@Test
	void deleteSqlFromModelChangesNothing() throws Exception {
		fake.queuePlan(new Plan(Plan.Action.QUERY, "DELETE FROM documents", null));
		fake.queuePlan(new Plan(Plan.Action.QUERY, "WITH x AS (DELETE FROM documents RETURNING id) SELECT * FROM x", null));

		chat(tokenA, QUESTION).andExpect(status().isOk()).andExpect(jsonPath("$.answer").value(CANT_LOOK_UP));

		mvc.perform(get("/api/documents").header("Authorization", bearer(tokenA)))
				.andExpect(status().isOk())
				.andExpect(content -> assertThat(content.getResponse().getContentAsString()).contains("Croma"));
	}

	@Test
	void notConfiguredIs503() throws Exception {
		fake.throwOnPlan(new AssistantException(AssistantException.NOT_CONFIGURED, "The assistant isn't set up."));

		chat(tokenA, QUESTION)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"))
				.andExpect(jsonPath("$.message").value(containsString("isn't set up")));
	}

	@Test
	void geminiFailureIs503() throws Exception {
		fake.throwOnPlan(new AssistantException(AssistantException.FAILED, "Gemini is busy right now."));

		chat(tokenA, QUESTION)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("ASSISTANT_FAILED"));
	}

	@Test
	void emptyChatIsRejected() throws Exception {
		chat(tokenA, "[]").andExpect(status().isBadRequest());
	}

	@Test
	void moreThanTenMessagesAreRejected() throws Exception {
		String eleven = "[" + String.join(",", Collections.nCopies(11, "{\"role\": \"user\", \"text\": \"hi\"}")) + "]";
		chat(tokenA, eleven).andExpect(status().isBadRequest());
	}

	@Test
	void lastMessageMustBeFromTheUser() throws Exception {
		chat(tokenA, "[{\"role\": \"assistant\", \"text\": \"hi\"}]").andExpect(status().isBadRequest());
	}

	@Test
	void unknownRoleIsRejected() throws Exception {
		chat(tokenA, "[{\"role\": \"system\", \"text\": \"hi\"}]").andExpect(status().isBadRequest());
	}

	@Test
	void tooLongMessageIsRejected() throws Exception {
		chat(tokenA, "[{\"role\": \"user\", \"text\": \"" + "x".repeat(2001) + "\"}]").andExpect(status().isBadRequest());
	}

	@Test
	void needsToken() throws Exception {
		postJson("/api/assistant/chat", "{\"messages\": " + QUESTION + "}").andExpect(status().isUnauthorized());
	}

	private ResultActions chat(String token, String messagesJson) throws Exception {
		return mvc.perform(post("/api/assistant/chat")
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"messages\": " + messagesJson + "}"));
	}

	private static String bill(String seller) {
		return """
				{"sellerName": "%s", "purchaseDate": "2026-10-01", "totalAmount": 100, "items": [{"productName": "Thing"}]}
				""".formatted(seller);
	}
}
