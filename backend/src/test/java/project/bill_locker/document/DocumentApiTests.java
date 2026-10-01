package project.bill_locker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.ApiTest;

/** Uploading, listing, viewing, downloading and deleting documents. */
class DocumentApiTests extends ApiTest {

	/** The smallest content that starts like a real PDF. */
	private static final byte[] PDF = "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

	@Autowired
	private DocumentFileRepository documentFiles;

	@Test
	void uploadListViewDownloadAndDeleteAPdf() throws Exception {
		String token = registerAndGetToken(uniqueEmail("docs"));

		String uploaded = mvc.perform(multipart("/api/documents/upload")
						.file(new MockMultipartFile("file", "Philips_Invoice.pdf", "application/pdf", PDF))
						.param("documentType", "INVOICE")
						.header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fileName").value("Philips_Invoice.pdf"))
				.andExpect(jsonPath("$.mimeType").value("application/pdf"))
				.andExpect(jsonPath("$.fileSize").value(PDF.length))
				.andExpect(jsonPath("$.documentType").value("INVOICE"))
				.andExpect(jsonPath("$.processingStatus").value("UPLOADED"))
				.andExpect(jsonPath("$.source").value("UPLOAD"))
				.andExpect(jsonPath("$.productId").value(nullValue()))
				.andExpect(jsonPath("$.createdAt").isString())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(uploaded, "$.id");

		mvc.perform(get("/api/documents").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(id));

		mvc.perform(get("/api/documents/" + id).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.fileName").value("Philips_Invoice.pdf"))
				.andExpect(jsonPath("$.extraction").value(nullValue()))
				.andExpect(jsonPath("$.extractedText").value(nullValue()));

		mvc.perform(get("/api/documents/" + id + "/download").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(header().string(CONTENT_DISPOSITION, containsString("inline")))
				.andExpect(content().bytes(PDF));

		mvc.perform(delete("/api/documents/" + id).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/documents/" + id).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
		assertThat(documentFiles.existsById(UUID.fromString(id))).as("file bytes deleted too").isFalse();
	}

	@Test
	void theContentDecidesTheFileType() throws Exception {
		String token = registerAndGetToken(uniqueEmail("types"));

		upload(token, "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
		// A PNG renamed to .pdf: the bytes don't match the name.
		upload(token, "fake.pdf", "application/pdf", PNG)
				.andExpect(status().isUnsupportedMediaType());
		// A real PNG is accepted; its type comes from the bytes, not from what the browser said.
		upload(token, "photo.png", "application/octet-stream", PNG)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mimeType").value("image/png"))
				.andExpect(jsonPath("$.documentType").value("OTHER"));
	}

	@Test
	void anEmptyOrMissingFileIsRejected() throws Exception {
		String token = registerAndGetToken(uniqueEmail("empty"));

		upload(token, "empty.pdf", "application/pdf", new byte[0])
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
		mvc.perform(multipart("/api/documents/upload").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
	}

	@Test
	void usersOnlySeeTheirOwnDocuments() throws Exception {
		String asha = registerAndGetToken(uniqueEmail("asha"));
		String bob = registerAndGetToken(uniqueEmail("bob"));
		String ashasDocument = JsonPath.read(upload(asha, "bill.pdf", "application/pdf", PDF)
				.andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/api/documents").header(AUTHORIZATION, bearer(bob)))
				.andExpect(jsonPath("$", hasSize(0)));
		// For Bob, Asha's document doesn't exist: 404, not 403.
		mvc.perform(get("/api/documents/" + ashasDocument).header(AUTHORIZATION, bearer(bob)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/documents/" + ashasDocument + "/download").header(AUTHORIZATION, bearer(bob)))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/documents/" + ashasDocument).header(AUTHORIZATION, bearer(bob)))
				.andExpect(status().isNotFound());

		mvc.perform(get("/api/documents/" + ashasDocument).header(AUTHORIZATION, bearer(asha)))
				.andExpect(status().isOk());
	}

	@Test
	void theListCanBeFilteredByType() throws Exception {
		String token = registerAndGetToken(uniqueEmail("filter"));
		upload(token, "invoice.pdf", "application/pdf", PDF).andExpect(status().isCreated());
		mvc.perform(multipart("/api/documents/upload")
						.file(new MockMultipartFile("file", "card.pdf", "application/pdf", PDF))
						.param("documentType", "WARRANTY_CARD")
						.header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated());

		mvc.perform(get("/api/documents").param("documentType", "WARRANTY_CARD").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].fileName").value("card.pdf"));
		mvc.perform(get("/api/documents").param("documentType", "NOT_A_TYPE").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void documentsNeedALogin() throws Exception {
		mvc.perform(get("/api/documents"))
				.andExpect(status().isUnauthorized());
		mvc.perform(multipart("/api/documents/upload").file(new MockMultipartFile("file", "bill.pdf", "application/pdf", PDF)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void fileNamesNeverKeepAPath() {
		assertThat(DocumentService.cleanFileName("C:\\fakepath\\bill.pdf")).isEqualTo("bill.pdf");
		assertThat(DocumentService.cleanFileName("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
		assertThat(DocumentService.cleanFileName("in\u0000voice.pdf")).isEqualTo("invoice.pdf");
	}

	private ResultActions upload(String token, String fileName, String contentType, byte[] content) throws Exception {
		return mvc.perform(multipart("/api/documents/upload")
				.file(new MockMultipartFile("file", fileName, contentType, content))
				.header(AUTHORIZATION, bearer(token)));
	}
}
