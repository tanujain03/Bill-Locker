package project.bill_locker.document;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Upload, open, download and delete a document. */
class DocumentApiTests extends DocumentApiTestBase {

	@Test
	void uploadStoresPdfAndReturnsDetail() throws Exception {
		String token = registerAndGetToken(uniqueEmail("up"));
		upload(token, "bill.pdf", "application/pdf", pdfBytes())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.status").value("UPLOADED"))
				.andExpect(jsonPath("$.fileName").value("bill.pdf"))
				.andExpect(jsonPath("$.contentType").value("application/pdf"))
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.documentUrl").value(endsWith("/download")));
	}

	@Test
	void uploadRejectsFakePdf() throws Exception {
		String token = registerAndGetToken(uniqueEmail("fake"));
		// Named .pdf and labelled as a PDF, but the bytes say otherwise.
		upload(token, "bill.pdf", "application/pdf", "hello".getBytes(StandardCharsets.US_ASCII))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
	}

	@Test
	void uploadRejectsEmptyFile() throws Exception {
		String token = registerAndGetToken(uniqueEmail("empty"));
		upload(token, "bill.pdf", "application/pdf", new byte[0])
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("FILE_EMPTY"));
	}

	@Test
	void uploadRejectsTooLargeFile() throws Exception {
		String token = registerAndGetToken(uniqueEmail("big"));
		byte[] big = Arrays.copyOf(pdfBytes(), 10 * 1024 * 1024 + 1);
		upload(token, "big.pdf", "application/pdf", big)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
	}

	@Test
	void uploadNeedsToken() throws Exception {
		mvc.perform(multipart("/api/documents/upload")
						.file(new MockMultipartFile("file", "bill.pdf", "application/pdf", pdfBytes())))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void downloadReturnsSameBytes() throws Exception {
		String token = registerAndGetToken(uniqueEmail("down"));
		String id = uploadPdf(token, "bill.pdf");
		mvc.perform(get("/api/documents/" + id + "/download").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(content().bytes(pdfBytes()))
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andExpect(header().string("Content-Disposition", containsString("inline")))
				.andExpect(header().string("Content-Disposition", containsString("bill.pdf")));
	}

	@Test
	void otherUserGets404Everywhere() throws Exception {
		String owner = registerAndGetToken(uniqueEmail("owner"));
		String other = registerAndGetToken(uniqueEmail("other"));
		String id = uploadPdf(owner, "bill.pdf");

		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(other)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
		mvc.perform(get("/api/documents/" + id + "/download").header("Authorization", bearer(other)))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/documents/" + id).header("Authorization", bearer(other)))
				.andExpect(status().isNotFound());
		// …and the owner still has it.
		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(owner)))
				.andExpect(status().isOk());
	}

	@Test
	void deleteRemovesDocument() throws Exception {
		String token = registerAndGetToken(uniqueEmail("del"));
		String id = uploadPdf(token, "bill.pdf");
		mvc.perform(delete("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(status().isNotFound());
	}
}
