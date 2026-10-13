package project.bill_locker.common;

import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A problem to report to the browser, e.g. 409 EMAIL_ALREADY_REGISTERED. Services
 * throw it; {@link GlobalExceptionHandler} turns it into the JSON error response.
 */
@Getter
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	/** Form field name → message shown under that input (or null). */
	private final Map<String, String> fieldErrors;
	/** The existing document the error is about, e.g. the earlier upload of a DUPLICATE_FILE (or null). */
	private final UUID documentId;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, null);
	}

	public ApiException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
		this(status, code, message, fieldErrors, null);
	}

	public ApiException(HttpStatus status, String code, String message, Map<String, String> fieldErrors,
			UUID documentId) {
		super(message);
		this.documentId = documentId;
		this.status = status;
		this.code = code;
		this.fieldErrors = fieldErrors;
	}
}
