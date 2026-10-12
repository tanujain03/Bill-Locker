package project.bill_locker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.FakeDetailExtractor;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.ai.ExtractionException;

/** Documents queued for reading (e.g. by Gmail import) are read by the background worker. */
class DocumentReadWorkerTests extends DocumentApiTestBase {

	@Autowired
	private DocumentService documentService;
	@Autowired
	private DocumentReadWorker worker;
	@Autowired
	private FakeDetailExtractor fake;
	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void resetAi() {
		fake.reset();
		// The worker takes the oldest queued document of anyone: leave nothing queued.
		while (worker.runOnce()) {
			// drain
		}
		fake.reset();
	}

	private UUID userId(String token) throws Exception {
		String body = mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	/** Queues a PDF the way the Gmail import does; returns the document id. */
	private String queue(String token) throws Exception {
		return documentService.createFromBytes(userId(token), "bill.pdf", pdfBytes(), "me@gmail.com").id().toString();
	}

	private ResultActions show(String token, String id) throws Exception {
		return mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)));
	}

	@Test
	void queuedDocumentIsReadByTheWorker() throws Exception {
		String token = registerAndGetToken(uniqueEmail("queued"));
		String id = queue(token);
		show(token, id).andExpect(jsonPath("$.readQueued").value(true))
				.andExpect(jsonPath("$.sourceGmail").value("me@gmail.com"));

		assertThat(worker.runOnce()).isTrue();

		show(token, id).andExpect(jsonPath("$.status").value("EXTRACTED"))
				.andExpect(jsonPath("$.readQueued").value(false))
				.andExpect(jsonPath("$.sellerName").value("Croma"));
		assertThat(worker.runOnce()).isFalse();
	}

	@Test
	void failedReadLeavesItUploadedWithReason() throws Exception {
		String token = registerAndGetToken(uniqueEmail("failed"));
		String id = queue(token);
		fake.willFail(new ExtractionException(ExtractionException.FAILED, "Gemini is busy right now."));

		worker.runOnce();

		show(token, id).andExpect(jsonPath("$.status").value("UPLOADED"))
				.andExpect(jsonPath("$.readQueued").value(false))
				.andExpect(jsonPath("$.readError").value("Gemini is busy right now."));
	}

	@Test
	void aiNotConfiguredIsReportedAsReadError() throws Exception {
		String token = registerAndGetToken(uniqueEmail("nokey"));
		String id = queue(token);
		fake.willFail(new ExtractionException(ExtractionException.NOT_CONFIGURED, "not set up"));

		worker.runOnce();

		show(token, id).andExpect(jsonPath("$.readError").value(containsString("AI reading is not set up")));
	}

	@Test
	void unstorableAnswerLeavesTheQueueWithReason() throws Exception {
		String token = registerAndGetToken(uniqueEmail("toolong"));
		String id = queue(token);
		// 600 characters do not fit seller_name (500): the write fails when it is flushed.
		DocumentDetails sample = FakeDetailExtractor.sampleInvoice();
		fake.willReturn(new DocumentDetails(sample.documentType(), sample.documentNumber(), "x".repeat(600),
				sample.sellerAddress(), sample.sellerContact(), sample.buyerName(), sample.buyerAddress(),
				sample.buyerEmail(), sample.purchaseDate(), sample.taxAmount(), sample.totalAmount(), sample.items()));

		assertThat(worker.runOnce()).isTrue();

		show(token, id).andExpect(jsonPath("$.status").value("UPLOADED"))
				.andExpect(jsonPath("$.readQueued").value(false))
				.andExpect(jsonPath("$.readError").isNotEmpty());
		assertThat(worker.runOnce()).isFalse();
	}

	@Test
	void missingFileLeavesTheQueueWithReason() throws Exception {
		String token = registerAndGetToken(uniqueEmail("nofile"));
		String id = queue(token);
		jdbc.update("delete from document_files where document_id = ?", UUID.fromString(id));

		assertThat(worker.runOnce()).isTrue();

		show(token, id).andExpect(jsonPath("$.readQueued").value(false))
				.andExpect(jsonPath("$.readError").isNotEmpty());
		assertThat(worker.runOnce()).isFalse();
	}

	@Test
	void deletedWhileQueuedIsSkipped() throws Exception {
		String token = registerAndGetToken(uniqueEmail("deleted"));
		String id = queue(token);
		mvc.perform(delete("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(status().isNoContent());

		assertThat(worker.runOnce()).isFalse();
	}

	@Test
	void savedWhileQueuedKeepsUserDetails() throws Exception {
		String token = registerAndGetToken(uniqueEmail("saved"));
		String id = queue(token);
		mvc.perform(put("/api/documents/" + id).header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sellerName\": \"Mine\", \"items\": [{\"productName\": \"Fan\"}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.readQueued").value(false));

		assertThat(worker.runOnce()).isFalse();

		show(token, id).andExpect(jsonPath("$.sellerName").value("Mine"))
				.andExpect(jsonPath("$.status").value("SAVED"));
	}

	@Test
	void listShowsReadingState() throws Exception {
		String token = registerAndGetToken(uniqueEmail("list"));
		queue(token);

		mvc.perform(get("/api/documents").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].readQueued").value(true));
	}

	@Test
	void createFromBytesChecksTheFileLikeUpload() throws Exception {
		String token = registerAndGetToken(uniqueEmail("check"));
		UUID userId = userId(token);

		assertThatThrownBy(() -> documentService.createFromBytes(userId, "a.txt",
				"just text".getBytes(StandardCharsets.US_ASCII), "me@gmail.com"))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getCode()).isEqualTo("INVALID_FILE_TYPE");

		byte[] tooBig = new byte[(int) DocumentService.MAX_FILE_BYTES + 1];
		assertThatThrownBy(() -> documentService.createFromBytes(userId, "big.pdf", tooBig, "me@gmail.com"))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getCode()).isEqualTo("FILE_TOO_LARGE");

		assertThatThrownBy(() -> documentService.createFromBytes(userId, "e.pdf", new byte[0], "me@gmail.com"))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getCode()).isEqualTo("FILE_EMPTY");
	}
}
