package project.bill_locker.document.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import project.bill_locker.document.DocumentDetails;

/** The HTTP call to Gemini, against a fake server (nothing leaves this computer). */
class GeminiDetailExtractorTests {

	private static final String URL =
			"https://generativelanguage.googleapis.com/v1beta/models/test-model:generateContent";
	private static final byte[] FILE = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

	private MockRestServiceServer gemini;
	private GeminiDetailExtractor extractor;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		gemini = MockRestServiceServer.bindTo(builder).build();
		extractor = new GeminiDetailExtractor(builder.build(), "test-key", List.of("test-model"));
	}

	/** Gemini's reply wraps the JSON it wrote in candidates[0].content.parts[0].text. */
	private static String reply(String modelJson) {
		String escaped = modelJson.replace("\\", "\\\\").replace("\"", "\\\"");
		return """
				{"candidates": [{"content": {"parts": [{"text": "%s"}]}}]}
				""".formatted(escaped);
	}

	@Test
	void sendsFileAndSchemaAndReadsAnswer() {
		gemini.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-goog-api-key", "test-key"))
				.andExpect(jsonPath("$.contents[0].parts[1].inline_data.mime_type").value("application/pdf"))
				.andExpect(jsonPath("$.contents[0].parts[1].inline_data.data")
						.value(Base64.getEncoder().encodeToString(FILE)))
				.andExpect(jsonPath("$.generationConfig.response_mime_type").value("application/json"))
				.andExpect(jsonPath("$.generationConfig.response_schema.properties.items.type").value("ARRAY"))
				.andRespond(withSuccess(reply("{\"sellerName\": \"Croma\", \"items\": [{\"productName\": \"Phone\"}]}"),
						MediaType.APPLICATION_JSON));

		DocumentDetails details = extractor.extract(FILE, "application/pdf");

		assertThat(details.sellerName()).isEqualTo("Croma");
		assertThat(details.items()).hasSize(1);
		gemini.verify();
	}

	@Test
	void quotaUsedUpGivesFriendlyMessage() {
		gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> extractor.extract(FILE, "application/pdf"))
				.isInstanceOf(ExtractionException.class)
				.hasMessageContaining("free Gemini limit")
				.extracting("code").isEqualTo(ExtractionException.FAILED);
	}

	@Test
	void badKeyGivesCheckKeyMessage() {
		gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThatThrownBy(() -> extractor.extract(FILE, "application/pdf"))
				.isInstanceOf(ExtractionException.class)
				.hasMessageContaining("GEMINI_API_KEY");
	}

	@Test
	void unopenableFileIsNotBlamedOnTheKey() {
		// Gemini answers 400 INVALID_ARGUMENT when it can't open the file (e.g. a broken PDF).
		gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertThatThrownBy(() -> extractor.extract(FILE, "application/pdf"))
				.isInstanceOf(ExtractionException.class)
				.hasMessageContaining("couldn't open this file")
				.hasMessageNotContaining("GEMINI_API_KEY");
	}

	@Test
	void serverErrorGivesTryAgainMessage() {
		// Gemini answers 503 "high demand" when the model is overloaded.
		gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> extractor.extract(FILE, "application/pdf"))
				.isInstanceOf(ExtractionException.class)
				.hasMessageContaining("busy")
				.hasMessageContaining("try again");
	}

	@Test
	void noCandidatesGivesNoAnswerMessage() {
		gemini.expect(requestTo(URL))
				.andRespond(withSuccess("{\"promptFeedback\": {\"blockReason\": \"OTHER\"}}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> extractor.extract(FILE, "application/pdf"))
				.isInstanceOf(ExtractionException.class)
				.hasMessageContaining("no answer");
	}

	// ---- Backup models: used only when a model is busy ------------------------------

	private static final String BACKUP_URL =
			"https://generativelanguage.googleapis.com/v1beta/models/backup-model:generateContent";

	private GeminiDetailExtractor withBackup() {
		RestClient.Builder builder = RestClient.builder();
		gemini = MockRestServiceServer.bindTo(builder).build();
		return new GeminiDetailExtractor(builder.build(), "test-key", List.of("test-model", "backup-model"));
	}

	@Test
	void busyModelFallsBackToTheNextOne() {
		GeminiDetailExtractor twoModels = withBackup();
		gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		gemini.expect(requestTo(BACKUP_URL))
				.andRespond(withSuccess(reply("{\"sellerName\": \"Croma\"}"), MediaType.APPLICATION_JSON));

		assertThat(twoModels.extract(FILE, "application/pdf").sellerName()).isEqualTo("Croma");
		gemini.verify();
	}

	@Test
	void quotaUsedUpOnOneModelAlsoFallsBack() {
		// Free-tier limits are per model, so another model may still have quota left.
		GeminiDetailExtractor twoModels = withBackup();
		gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		gemini.expect(requestTo(BACKUP_URL))
				.andRespond(withSuccess(reply("{\"sellerName\": \"Croma\"}"), MediaType.APPLICATION_JSON));

		assertThat(twoModels.extract(FILE, "application/pdf").sellerName()).isEqualTo("Croma");
	}

	@Test
	void allModelsBusyGivesBusyMessage() {
		GeminiDetailExtractor twoModels = withBackup();
		gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		gemini.expect(requestTo(BACKUP_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> twoModels.extract(FILE, "application/pdf"))
				.isInstanceOf(ExtractionException.class)
				.hasMessageContaining("busy");
		gemini.verify();
	}

	@Test
	void badKeyDoesNotTryOtherModels() {
		// Only one request is expected: a wrong key is wrong for every model.
		GeminiDetailExtractor twoModels = withBackup();
		gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThatThrownBy(() -> twoModels.extract(FILE, "application/pdf"))
				.isInstanceOf(ExtractionException.class)
				.hasMessageContaining("GEMINI_API_KEY");
		gemini.verify();
	}
}
