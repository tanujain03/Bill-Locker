package project.bill_locker.document.ai;

import lombok.Getter;

/**
 * Reading a document failed. {@code code} is AI_NOT_CONFIGURED (no API key) or
 * EXTRACTION_FAILED (the AI was unreachable, refused, or answered nonsense); the
 * message is shown to the user.
 */
@Getter
public class ExtractionException extends RuntimeException {

	public static final String NOT_CONFIGURED = "AI_NOT_CONFIGURED";
	public static final String FAILED = "EXTRACTION_FAILED";

	private final String code;

	public ExtractionException(String code, String message) {
		super(message);
		this.code = code;
	}
}
