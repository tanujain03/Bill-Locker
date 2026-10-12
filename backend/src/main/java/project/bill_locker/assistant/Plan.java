package project.bill_locker.assistant;

/** Gemini's first step: run a query (sql), or just reply (greetings, "I can't delete", ...). */
public record Plan(Action action, String sql, String reply) {

	public enum Action {
		QUERY, REPLY
	}
}
