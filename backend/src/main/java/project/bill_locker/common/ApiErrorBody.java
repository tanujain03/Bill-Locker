package project.bill_locker.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * The JSON body of every error response, the same shape as {@code ApiErrorBody}
 * in the frontend: {@code { "success": false, "code": "...", "message": "..." }}.
 * {@code fieldErrors} is added only when form fields are invalid.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(boolean success, String code, String message, Map<String, String> fieldErrors) {

	public ApiErrorBody(String code, String message, Map<String, String> fieldErrors) {
		this(false, code, message, fieldErrors);
	}
}
