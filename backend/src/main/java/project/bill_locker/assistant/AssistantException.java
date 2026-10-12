package project.bill_locker.assistant;

/** The AI couldn't be asked: not set up, or Gemini failed. The message is safe to show. */
public class AssistantException extends RuntimeException {

	public static final String NOT_CONFIGURED = "AI_NOT_CONFIGURED";
	public static final String FAILED = "ASSISTANT_FAILED";

	private final String code;

	public AssistantException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
