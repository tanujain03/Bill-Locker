package project.bill_locker.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Catches exceptions thrown by any controller or service and turns them into the
 * API's error JSON, so the browser never sees a stack trace or SQL.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Errors we throw on purpose, e.g. 401 INVALID_CREDENTIALS. */
	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiErrorBody> handleApiException(ApiException ex) {
		return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFieldErrors());
	}

	/** A {@code @Valid} request body broke a rule: tell the form which fields are wrong. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorBody> handleInvalidFields(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please check the highlighted fields.", fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorBody> handleUnreadableBody(HttpMessageNotReadableException ex) {
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request body is not valid JSON.", null);
	}

	/** Anything else. Spring's own errors (unknown URL, wrong HTTP method…) keep their status. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorBody> handleOther(Exception ex) {
		if (ex instanceof ErrorResponse springError) {
			HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
			return respond(status, status.name(), "The request could not be processed.", null);
		}
		log.error("Unexpected error", ex);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR",
				"Something went wrong on our side. Please try again.", null);
	}

	private static ResponseEntity<ApiErrorBody> respond(HttpStatus status, String code, String message,
			Map<String, String> fieldErrors) {
		return ResponseEntity.status(status).body(new ApiErrorBody(code, message, fieldErrors));
	}
}
