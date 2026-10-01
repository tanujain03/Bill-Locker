package project.bill_locker.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A problem to report to the client, e.g. 404 DOCUMENT_NOT_FOUND. Services throw
 * it; {@link GlobalExceptionHandler} turns it into the JSON error response.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final Map<String, String> fieldErrors;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, null);
	}

	/** {@code fieldErrors} maps form field names to messages shown next to those inputs. */
	public ApiException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
		super(message);
		this.status = status;
		this.code = code;
		this.fieldErrors = fieldErrors;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}
}
