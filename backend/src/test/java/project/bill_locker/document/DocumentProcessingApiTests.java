package project.bill_locker.document;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import project.bill_locker.ApiTest;
import project.bill_locker.TestFiles;
import project.bill_locker.processing.DocumentProcessor;

/**
 * Upload → background reading → details in the API. The scheduled reader is off in
 * tests (see ApiTest), so each test runs it at a known moment.
 */
class DocumentProcessingApiTests extends ApiTest {

	@Autowired
	private DocumentProcessor processor;

	@Test
	void anUploadedInvoiceIsReadAndItsDetailsFound() throws Exception {
		String token = registerAndGetToken(uniqueEmail("reader"));
		byte[] invoice = TestFiles.pdfWithText("TAX INVOICE", "Invoice No: ME/2026-27/006745", "Invoice Date: 27/09/2026",
				"Philips Air Fryer HD9252/90 1 8,999.00", "Grand Total Rs. 8,999.00", "2 Years Warranty");
		String id = upload(token, "invoice.pdf", "application/pdf", invoice);

		processor.processPendingDocuments();

		mvc.perform(get("/api/documents/" + id).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.processingStatus").value("PROCESSED"))
				.andExpect(jsonPath("$.processingStage").value(nullValue()))
				.andExpect(jsonPath("$.extractedText", containsString("Invoice No: ME/2026-27/006745")))
				.andExpect(jsonPath("$.extraction.documentType").value("INVOICE"))
				.andExpect(jsonPath("$.extraction.invoiceNumber").value("ME/2026-27/006745"))
				.andExpect(jsonPath("$.extraction.purchaseDate").value("2026-09-27"))
				.andExpect(jsonPath("$.extraction.purchasePrice").value(8999.00))
				.andExpect(jsonPath("$.extraction.warrantyMonths").value(24))
				.andExpect(jsonPath("$.extraction.brand").value("Philips"))
				.andExpect(jsonPath("$.extraction.serialNumber").value(nullValue()))
				.andExpect(jsonPath("$.extraction.confidence.invoiceNumber").value(0.85));
	}

	@Test
	void aPhotoWithNoTextFailsWithAHelpfulMessageAndCanBeReadAgain() throws Exception {
		String token = registerAndGetToken(uniqueEmail("blank"));
		String id = upload(token, "blank.png", "image/png", TestFiles.blankPng());

		processor.processPendingDocuments();

		mvc.perform(get("/api/documents/" + id).header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.processingStatus").value("FAILED"))
				.andExpect(jsonPath("$.errorMessage", containsString("No text was found")));

		mvc.perform(post("/api/documents/" + id + "/reprocess").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.processingStatus").value("UPLOADED"))
				.andExpect(jsonPath("$.errorMessage").value(nullValue()));
		// Already waiting to be read: asking again is refused.
		mvc.perform(post("/api/documents/" + id + "/reprocess").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_READY"));
	}

	private String upload(String token, String fileName, String contentType, byte[] content) throws Exception {
		String response = mvc.perform(multipart("/api/documents/upload")
						.file(new MockMultipartFile("file", fileName, contentType, content))
						.header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.processingStatus").value("UPLOADED"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}
}
