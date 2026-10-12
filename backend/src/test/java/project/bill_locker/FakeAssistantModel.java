package project.bill_locker;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import project.bill_locker.assistant.AssistantModel;
import project.bill_locker.assistant.ChatMessage;
import project.bill_locker.assistant.Plan;

/**
 * Stands in for Gemini in the assistant tests: never goes online. A test queues the plans it
 * should "decide" (default: reply "Hello"); the answer just echoes the rows, so tests can see
 * exactly what the query returned. It remembers each plan call's failed SQL and error.
 */
public class FakeAssistantModel implements AssistantModel {

	/** One plan() call: what the earlier query and its error were (null on the first try). */
	public record PlanCall(String failedSql, String error) {
	}

	private final Deque<Plan> plans = new ArrayDeque<>();
	private final List<PlanCall> planCalls = new ArrayList<>();
	private RuntimeException failure;

	@Override
	public synchronized Plan plan(List<ChatMessage> messages, LocalDate today, String failedSql, String error) {
		planCalls.add(new PlanCall(failedSql, error));
		if (failure != null) {
			throw failure;
		}
		return plans.isEmpty() ? new Plan(Plan.Action.REPLY, null, "Hello") : plans.removeFirst();
	}

	@Override
	public String answer(List<ChatMessage> messages, String sql, List<Map<String, Object>> rows) {
		return "rows=" + rows;
	}

	public synchronized void queuePlan(Plan plan) {
		plans.addLast(plan);
	}

	public synchronized void throwOnPlan(RuntimeException failure) {
		this.failure = failure;
	}

	public synchronized List<PlanCall> planCalls() {
		return List.copyOf(planCalls);
	}

	public synchronized void reset() {
		plans.clear();
		planCalls.clear();
		failure = null;
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary // wins over the real Gemini model
		FakeAssistantModel fakeAssistantModel() {
			return new FakeAssistantModel();
		}
	}
}
