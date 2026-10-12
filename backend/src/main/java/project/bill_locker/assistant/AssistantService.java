package project.bill_locker.assistant;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import project.bill_locker.common.ApiException;

/**
 * Answers one chat message in up to three steps:
 * 1. plan: Gemini decides to reply directly, or writes a SELECT;
 * 2. query: {@link ReadOnlyQueryRunner} runs it for this user only (one retry if it fails);
 * 3. answer: Gemini writes the answer from the rows.
 * No @Transactional here: only the query needs a transaction, and Gemini calls are slow.
 */
@Service
public class AssistantService {

	static final String CANT_LOOK_UP = "I couldn't look that up. Try asking in a different way.";

	private final AssistantModel model;
	private final ReadOnlyQueryRunner queries;
	private final Clock clock;

	public AssistantService(AssistantModel model, ReadOnlyQueryRunner queries, Clock clock) {
		this.model = model;
		this.queries = queries;
		this.clock = clock;
	}

	public ChatResponse chat(UUID userId, List<ChatMessage> messages) {
		if (!"user".equals(messages.getLast().role())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please ask a question.",
					Map.of("messages", "The last message must be from the user."));
		}
		try {
			LocalDate today = LocalDate.now(clock);
			Plan plan = model.plan(messages, today, null, null);
			if (plan.action() == Plan.Action.REPLY || plan.sql() == null) {
				return new ChatResponse(plan.reply() == null ? CANT_LOOK_UP : plan.reply(), null);
			}
			String sql = plan.sql();
			List<Map<String, Object>> rows;
			try {
				rows = queries.run(userId, sql);
			} catch (QueryRejectedException firstError) {
				// Gemini sees the error (e.g. a wrong column name) and gets one chance to fix its query.
				Plan retry = model.plan(messages, today, sql, firstError.getMessage());
				if (retry.action() == Plan.Action.REPLY || retry.sql() == null) {
					return new ChatResponse(retry.reply() == null ? CANT_LOOK_UP : retry.reply(), null);
				}
				sql = retry.sql();
				try {
					rows = queries.run(userId, sql);
				} catch (QueryRejectedException secondError) {
					return new ChatResponse(CANT_LOOK_UP, sql);
				}
			}
			return new ChatResponse(model.answer(messages, sql, rows), sql);
		} catch (AssistantException e) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, e.getCode(), e.getMessage());
		}
	}
}
