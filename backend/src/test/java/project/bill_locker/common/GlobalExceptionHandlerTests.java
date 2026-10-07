package project.bill_locker.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Errors that MockMvc can't produce through a real request. */
class GlobalExceptionHandlerTests {

	@Test
	void uploadOverServerLimitIsFileTooLarge() {
		// Thrown by the servlet container while it reads a request over 10 MB.
		var response = new GlobalExceptionHandler().handleUploadTooLarge(new MaxUploadSizeExceededException(10));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().code()).isEqualTo("FILE_TOO_LARGE");
	}
}
