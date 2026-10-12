package project.bill_locker.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The assistant's HTTP calls to Gemini, against a fake server (nothing leaves this computer). */
class GeminiAssistantModelTests {

	private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
	private static final List<ChatMessage> QUESTION = List.of(new ChatMessage("user", "How much did I spend?"));

	private MockRestServiceServer gemini;

	private GeminiAssistantModel model(String... models) {
		RestClient.Builder builder = RestClient.builder();
		gemini = MockRestServiceServer.bindTo(builder).build();
		return new GeminiAssistantModel(builder.build(), "test-key", List.of(models));
	}

	/** Gemini's reply wraps what it wrote in candidates[0].content.parts[0].text. */
	private static String reply(String text) {
		String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
		return """
				{"candidates": [{"content": {"parts": [{"text": "%s"}]}}]}
				""".formatted(escaped);
	}

	@Test
	void planReadsQueryAnswer() {
		GeminiAssistantModel model = model("m1");
		gemini.expect(requestTo(URL.formatted("m1")))
				.andExpect(header("x-goog-api-key", "test-key"))
				.andExpect(jsonPath("$.system_instruction.parts[0].text").value(containsString("ai_documents")))
				.andExpect(jsonPath("$.system_instruction.parts[0].text").value(containsString("2026-10-07")))
				.andExpect(jsonPath("$.contents[0].role").value("user"))
				.andExpect(jsonPath("$.contents[0].parts[0].text").value("How much did I spend?"))
				.andExpect(jsonPath("$.generationConfig.response_mime_type").value("application/json"))
				.andRespond(withSuccess(reply("{\"action\": \"QUERY\", \"sql\": \"SELECT 1\", \"reply\": null}"),
						MediaType.APPLICATION_JSON));

		assertThat(model.plan(QUESTION, TODAY, null, null)).isEqualTo(new Plan(Plan.Action.QUERY, "SELECT 1", null));
		gemini.verify();
	}

	@Test
	void earlierAnswersAreSentAsModelTurns() {
		GeminiAssistantModel model = model("m1");
		gemini.expect(requestTo(URL.formatted("m1")))
				.andExpect(jsonPath("$.contents[1].role").value("model"))
				.andExpect(jsonPath("$.contents[1].parts[0].text").value("₹1,499"))
				.andRespond(withSuccess(reply("{\"action\": \"REPLY\", \"sql\": null, \"reply\": \"Hi\"}"),
						MediaType.APPLICATION_JSON));

		List<ChatMessage> chat = List.of(new ChatMessage("user", "Spent?"), new ChatMessage("assistant", "₹1,499"),
				new ChatMessage("user", "And last month?"));
		assertThat(model.plan(chat, TODAY, null, null)).isEqualTo(new Plan(Plan.Action.REPLY, null, "Hi"));
	}

	@Test
	void retrySendsTheFailedSqlAndError() {
		GeminiAssistantModel model = model("m1");
		gemini.expect(requestTo(URL.formatted("m1")))
				.andExpect(jsonPath("$.system_instruction.parts[0].text").value(containsString("SELECT x FROM ai_documents")))
				.andExpect(jsonPath("$.system_instruction.parts[0].text").value(containsString("column \"x\" does not exist")))
				.andRespond(withSuccess(reply("{\"action\": \"QUERY\", \"sql\": \"SELECT 2\", \"reply\": null}"),
						MediaType.APPLICATION_JSON));

		assertThat(model.plan(QUESTION, TODAY, "SELECT x FROM ai_documents", "column \"x\" does not exist").sql())
				.isEqualTo("SELECT 2");
	}

	@Test
	void answerSendsRowsAndReturnsText() {
		GeminiAssistantModel model = model("m1");
		gemini.expect(requestTo(URL.formatted("m1")))
				.andExpect(jsonPath("$.system_instruction.parts[0].text").value(containsString("Croma")))
				.andExpect(jsonPath("$.system_instruction.parts[0].text").value(containsString("SELECT seller_name")))
				.andRespond(withSuccess(reply("You spent ₹1,499 at Croma."), MediaType.APPLICATION_JSON));

		String answer = model.answer(QUESTION, "SELECT seller_name FROM ai_documents",
				List.of(Map.of("seller_name", "Croma")));

		assertThat(answer).isEqualTo("You spent ₹1,499 at Croma.");
	}

	@Test
	void busyModelFallsBackToTheNextOne() {
		GeminiAssistantModel model = model("m1", "m2");
		gemini.expect(requestTo(URL.formatted("m1"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		gemini.expect(requestTo(URL.formatted("m2"))).andRespond(withSuccess(reply("Done"), MediaType.APPLICATION_JSON));

		assertThat(model.answer(QUESTION, "SELECT 1", List.of())).isEqualTo("Done");
	}

	@Test
	void quotaUsedUpGivesFriendlyMessage() {
		GeminiAssistantModel model = model("m1");
		gemini.expect(requestTo(URL.formatted("m1"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> model.plan(QUESTION, TODAY, null, null))
				.isInstanceOf(AssistantException.class)
				.hasMessageContaining("free Gemini limit")
				.extracting("code").isEqualTo(AssistantException.FAILED);
	}

	@Test
	void badKeyGivesCheckKeyMessage() {
		GeminiAssistantModel model = model("m1");
		gemini.expect(requestTo(URL.formatted("m1"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> model.plan(QUESTION, TODAY, null, null))
				.isInstanceOf(AssistantException.class)
				.hasMessageContaining("GEMINI_API_KEY");
	}
}
