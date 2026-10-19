package project.bill_locker.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class BatchUploadApiTests extends DocumentApiTestBase {

	private ResultActions queuedUpload(String token, String name, byte[] bytes) throws Exception {
		return mvc.perform(multipart("/api/documents/upload").param("read", "true")
				.file(new MockMultipartFile("file", name, "application/pdf", bytes))
				.header("Authorization", bearer(token)));
	}

	@Test
	void eachFileInABatchIsQueuedForBackgroundReadingAndRemainsAvailable() throws Exception {
		String token = registerAndGetToken(uniqueEmail("batch-up"));
		queuedUpload(token, "first.pdf", pdfBytes()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.readQueued").value(true));
		queuedUpload(token, "second.pdf", pdfBytes()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.readQueued").value(true));
		mvc.perform(get("/api/documents").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void duplicatesReturnExistingIdWithoutRequeueingOrChangingReviewedDetails() throws Exception {
		String token = registerAndGetToken(uniqueEmail("batch-duplicate"));
		byte[] bytes = pdfBytes();
		String response = upload(token, "original.pdf", "application/pdf", bytes)
				.andExpect(status().isCreated()).andExpect(jsonPath("$.readQueued").value(false))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(response, "$.id");
		mvc.perform(put("/api/documents/" + id).header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"sellerName\":\"Reviewed seller\",\"items\":[]}"))
				.andExpect(status().isOk());
		queuedUpload(token, "duplicate.pdf", bytes).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_FILE"))
				.andExpect(jsonPath("$.documentId").value(id));
		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.readQueued").value(false))
				.andExpect(jsonPath("$.sellerName").value("Reviewed seller"))
				.andExpect(jsonPath("$.status").value("SAVED"));
	}

	@Test
	void invalidFileDoesNotPreventALaterValidUpload() throws Exception {
		String token = registerAndGetToken(uniqueEmail("batch-invalid"));
		queuedUpload(token, "empty.pdf", new byte[0]).andExpect(status().isBadRequest());
		queuedUpload(token, "valid.pdf", pdfBytes()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.readQueued").value(true));
		mvc.perform(get("/api/documents").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.length()").value(1));
	}
}
