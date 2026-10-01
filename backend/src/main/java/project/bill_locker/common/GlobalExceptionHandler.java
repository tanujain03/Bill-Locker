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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Turns any exception thrown by a controller or service into the API's error
 * format, so the frontend always receives {@code { success:false, code, message }}
 * with a message that is safe to show (never a stack trace or SQL).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Errors we raise on purpose, e.g. 409 EMAIL_ALREADY_REGISTERED. */
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

	/** A value in the URL has the wrong format, e.g. {@code ?status=SOMETHING}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiErrorBody> handleWrongValue(MethodArgumentTypeMismatchException ex) {
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid value for '" + ex.getName() + "'.", null);
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<ApiErrorBody> handleMissingFile(MissingServletRequestPartException ex) {
		return respond(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Choose a file to upload.", null);
	}

	/** Bigger than spring.servlet.multipart.max-file-size in application.properties. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiErrorBody> handleTooLarge(MaxUploadSizeExceededException ex) {
		return respond(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "The file is larger than 10 MB.", null);
	}

	@ExceptionHandler(MultipartException.class)
	ResponseEntity<ApiErrorBody> handleBrokenUpload(MultipartException ex) {
		return respond(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", "The upload could not be read. Please try again.", null);
	}

	/** Anything else. Spring's own errors (unknown URL, wrong HTTP method…) keep their status. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorBody> handleOther(Exception ex) {
		if (ex instanceof ErrorResponse springError) {
			HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
			String message = status == HttpStatus.NOT_FOUND
					? "The requested item was not found."
					: "The request could not be processed.";
			return respond(status, status.name(), message, null);
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
