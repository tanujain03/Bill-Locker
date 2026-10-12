package project.bill_locker.assistant;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The AI behind the assistant. Real one: {@link GeminiAssistantModel}; tests use a fake. */
public interface AssistantModel {

	/**
	 * Decides how to answer the last message: a SQL query on the ai_* views, or a direct reply.
	 * {@code failedSql} and {@code error} are null, except on the one retry after a failed query.
	 */
	Plan plan(List<ChatMessage> messages, LocalDate today, String failedSql, String error);

	/** Writes the answer to the last message from the rows the query returned. */
	String answer(List<ChatMessage> messages, String sql, List<Map<String, Object>> rows);
}
