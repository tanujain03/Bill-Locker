package project.bill_locker.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.UUID;

/**
 * The JSON body of every error: {@code { "success": false, "code": "...", "message": "..." }}.
 * {@code fieldErrors} is added only when form fields are invalid, {@code documentId} only when
 * the error points at an existing document (409 DUPLICATE_FILE).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(boolean success, String code, String message, Map<String, String> fieldErrors,
		UUID documentId) {

	public ApiErrorBody(String code, String message, Map<String, String> fieldErrors) {
		this(code, message, fieldErrors, null);
	}

	public ApiErrorBody(String code, String message, Map<String, String> fieldErrors, UUID documentId) {
		this(false, code, message, fieldErrors, documentId);
	}
}
