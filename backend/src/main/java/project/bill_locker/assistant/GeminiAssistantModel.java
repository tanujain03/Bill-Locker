package project.bill_locker.assistant;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The assistant's two Gemini calls, through Gemini's REST API (like {@code GeminiDetailExtractor}).
 *
 * <p>Each request carries our instructions as the "system instruction" and the chat so far as
 * "contents" (the user's messages as "user", our earlier answers as "model").
 * Step 1 ({@link #plan}) answers in a fixed JSON shape: QUERY with a SELECT, or REPLY.
 * Step 2 ({@link #answer}) gets the rows the query returned and writes the answer as text.
 */
public class GeminiAssistantModel implements AssistantModel {

	private static final Logger log = LoggerFactory.getLogger(GeminiAssistantModel.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();

	static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

	private static final String PLAN_PROMPT = """
			You are the assistant inside Bill Locker, an app that keeps a person's bills, receipts and warranties.
			You answer questions about the signed-in user's own data. Today is %s.

			You can read these PostgreSQL views. They already contain only this user's rows:
			%s
			Decide how to answer the user's last message:
			- If it needs their data: action QUERY with ONE PostgreSQL SELECT on the views above in "sql".
			  - Use only the ai_* views. Never filter by user: the views already do that.
			  - "Spent" means SUM(total_amount) of ai_documents with status = 'SAVED', by purchase_date.
			  - Search names case-insensitively with ILIKE '%%word%%' on product_name, brand, seller_name or file_name.
			  - Use CURRENT_DATE for "this month", "last year" and so on.
			  - When the answer is about specific bills, select ai_documents.id too (and useful details).
			- If they ask you to delete, upload, edit, save, register, connect or send anything: action REPLY.
			  Say you can only look things up, and where they can do it (Documents page to upload, edit or
			  delete; Gmail import page to connect Gmail; Settings for their account).
			- Greetings, thanks, or questions that aren't about their data: action REPLY with a short answer.
			""";

	/** Added on the one retry, so Gemini can fix its own query. */
	private static final String RETRY = """

			Your previous query failed. Write a corrected one.
			Query: %s
			Error: %s
			""";

	private static final String ANSWER_PROMPT = """
			You are the assistant inside Bill Locker. Answer the user's last message using ONLY the rows below,
			which come from their own data (query: %s).
			Rows (JSON, at most 50): %s

			Rules:
			- Never invent values. No rows means nothing matched: say so.
			- Amounts are rupees: write them like ₹1,23,456.50. Dates like 7 Oct 2026.
			- When you mention a specific bill, add its link as the plain path /documents/<its id>
			  (not a Markdown link: the app turns the path into a link).
			- Short plain text (no Markdown tables or headings). Use a simple list only when listing several things.
			""";

	/** The plan's shape (Gemini's schema format: types in capitals). */
	private static final Map<String, Object> PLAN_SCHEMA = Map.of(
			"type", "OBJECT",
			"properties", Map.of(
					"action", Map.of("type", "STRING", "enum", List.of("QUERY", "REPLY")),
					"sql", Map.of("type", "STRING", "nullable", true, "description", "One SELECT, for QUERY"),
					"reply", Map.of("type", "STRING", "nullable", true, "description", "The answer, for REPLY")),
			"required", List.of("action"));

	private final RestClient restClient;
	private final String apiKey;
	/** Tried in this order; the next one only when the one before is busy or out of quota. */
	private final List<String> models;

	public GeminiAssistantModel(RestClient restClient, String apiKey, List<String> models) {
		if (models.isEmpty()) {
			throw new IllegalArgumentException("Set at least one GEMINI_MODEL");
		}
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.models = List.copyOf(models);
	}

	@Override
	public Plan plan(List<ChatMessage> messages, LocalDate today, String failedSql, String error) {
		String instructions = PLAN_PROMPT.formatted(today, AssistantDatabaseSetup.VIEWS_DESCRIPTION);
		if (failedSql != null) {
			instructions += RETRY.formatted(failedSql, error);
		}
		Map<String, Object> config = Map.of(
				"response_mime_type", "application/json",
				"response_schema", PLAN_SCHEMA,
				"temperature", 0); // 0 = no creativity: SQL should be exact
		String text = call(instructions, messages, config);
		try {
			JsonNode plan = JSON.readTree(text);
			Plan.Action action = Plan.Action.valueOf(plan.path("action").asString());
			return new Plan(action, textOrNull(plan.path("sql")), textOrNull(plan.path("reply")));
		} catch (JacksonException | IllegalArgumentException e) {
			log.warn("Gemini's plan wasn't in the expected shape: {}", text);
			throw new AssistantException(AssistantException.FAILED, "The assistant got confused. Please try again.");
		}
	}

	@Override
	public String answer(List<ChatMessage> messages, String sql, List<Map<String, Object>> rows) {
		String instructions = ANSWER_PROMPT.formatted(sql, JSON.writeValueAsString(rows));
		return call(instructions, messages, Map.of("temperature", 0.2)).strip();
	}

	/** Sends one request (trying the backup models when busy) and returns the text Gemini wrote. */
	private String call(String instructions, List<ChatMessage> messages, Map<String, Object> generationConfig) {
		String body = JSON.writeValueAsString(requestBody(instructions, messages, generationConfig));
		int lastStatus = 0;
		for (String model : models) {
			String reply;
			try {
				reply = restClient.post()
						.uri(URL, model)
						.header("x-goog-api-key", apiKey) // in a header, so the key never shows up in logged URLs
						.contentType(MediaType.APPLICATION_JSON)
						.body(body)
						.retrieve()
						.body(String.class);
			} catch (RestClientResponseException e) {
				lastStatus = e.getStatusCode().value();
				log.warn("Gemini model {} answered HTTP {}: {}", model, lastStatus, e.getResponseBodyAsString());
				// Busy (503) and quota (429) are per model, so another model may still work.
				if (lastStatus == 503 || lastStatus == 429) {
					continue;
				}
				throw new AssistantException(AssistantException.FAILED, messageFor(lastStatus));
			} catch (RestClientException e) { // no connection, timeout…
				log.warn("Could not reach Gemini: {}", e.getMessage());
				throw new AssistantException(AssistantException.FAILED, messageFor(0));
			}
			return answerText(reply);
		}
		throw new AssistantException(AssistantException.FAILED, messageFor(lastStatus));
	}

	private static Map<String, Object> requestBody(String instructions, List<ChatMessage> messages,
			Map<String, Object> generationConfig) {
		List<Map<String, Object>> contents = messages.stream()
				.map(m -> Map.<String, Object>of(
						"role", "assistant".equals(m.role()) ? "model" : "user",
						"parts", List.of(Map.of("text", m.text()))))
				.toList();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("system_instruction", Map.of("parts", List.of(Map.of("text", instructions))));
		body.put("contents", contents);
		body.put("generationConfig", generationConfig);
		return body;
	}

	private static String messageFor(int status) {
		if (status == 429) {
			return "The free Gemini limit is used up for now. Wait a minute (or until tomorrow) and try again.";
		}
		if (status == 401 || status == 403 || status == 404) {
			return "Gemini refused the request. Check GEMINI_API_KEY and GEMINI_MODEL in backend/.env.";
		}
		if (status == 503) {
			return "Gemini is busy right now. Please try again in a minute.";
		}
		return "The assistant can't reach Gemini right now. Please try again.";
	}

	/** Gemini's reply: {"candidates": [{"content": {"parts": [{"text": "..."}]}}]}. */
	private static String answerText(String reply) {
		try {
			JsonNode text = JSON.readTree(reply == null ? "" : reply)
					.path("candidates").path(0).path("content").path("parts").path(0).path("text");
			if (text.isString()) {
				return text.asString();
			}
		} catch (JacksonException e) {
			// falls through to "no answer"
		}
		log.warn("Gemini returned no answer: {}", reply);
		throw new AssistantException(AssistantException.FAILED, "Gemini returned no answer. Please try again.");
	}

	private static String textOrNull(JsonNode node) {
		return node.isString() && !node.asString().isBlank() ? node.asString() : null;
	}
}
