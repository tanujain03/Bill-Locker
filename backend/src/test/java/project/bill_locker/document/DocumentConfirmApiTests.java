package project.bill_locker.document;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.ApiTest;
import project.bill_locker.TestFiles;
import project.bill_locker.processing.DocumentProcessor;

/** "Confirm & Save": the checked details of a read document become a product with a warranty. */
class DocumentConfirmApiTests extends ApiTest {

	private static final LocalDate BOUGHT = LocalDate.now().minusDays(4);

	@Autowired
	private DocumentProcessor processor;

	@Test
	void confirmingAReadInvoiceCreatesAProductWithItsWarranty() throws Exception {
		String token = registerAndGetToken(uniqueEmail("confirm"));
		String documentId = uploadReadInvoice(token);

		String response = confirm(token, documentId, null, "Philips Air Fryer", 24)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.document.id").value(documentId))
				.andExpect(jsonPath("$.document.processingStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.document.documentType").value("INVOICE"))
				.andExpect(jsonPath("$.document.productName").value("Philips Air Fryer"))
				.andExpect(jsonPath("$.product.name").value("Philips Air Fryer"))
				.andExpect(jsonPath("$.product.categorySlug").value("kitchen"))
				.andExpect(jsonPath("$.product.warranty.expiryDate").value(BOUGHT.plusMonths(24).minusDays(1).toString()))
				.andExpect(jsonPath("$.product.documentCount").value(1))
				.andReturn().getResponse().getContentAsString();
		String productId = JsonPath.read(response, "$.product.id");

		mvc.perform(get("/api/documents/" + documentId).header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.productId").value(productId));
		// The warranty remembers which bill it came from.
		mvc.perform(get("/api/warranties").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[0].productId").value(productId))
				.andExpect(jsonPath("$[0].sourceDocumentId").value(documentId));

		// Saving twice is refused, and so is reading a saved document again.
		confirm(token, documentId, null, "Philips Air Fryer", 24)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_CONFIRMED"));
		mvc.perform(post("/api/documents/" + documentId + "/reprocess").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_CONFIRMED"));
	}

	@Test
	void aDocumentCanBeSavedIntoAnExistingProduct() throws Exception {
		String token = registerAndGetToken(uniqueEmail("existing"));
		String productId = createProduct(token, """
				{"name": "Air Fryer"}
				""");
		String documentId = uploadReadInvoice(token);

		confirm(token, documentId, productId, "Philips Air Fryer HD9252", 24)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.product.id").value(productId))
				.andExpect(jsonPath("$.product.name").value("Philips Air Fryer HD9252"))
				.andExpect(jsonPath("$.product.warranty.warrantyMonths").value(24));

		mvc.perform(get("/api/products").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void reviewMistakesComeBackPerFieldAndUnreadDocumentsWait() throws Exception {
		String token = registerAndGetToken(uniqueEmail("review"));
		String documentId = uploadReadInvoice(token);

		// The form's fields are named as in ProductInput ("name"), not "product.name".
		confirm(token, documentId, null, " ", 24)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.name").value("Product name is required"));

		String notReadYet = uploadFile(token, "new.pdf", "application/pdf", TestFiles.pdfWithText("Invoice"), null);
		confirm(token, notReadYet, null, "Something", 12)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_READY"));
	}

	@Test
	void aDocumentThatCouldNotBeReadCanBeSavedWithDetailsTypedIn() throws Exception {
		String token = registerAndGetToken(uniqueEmail("manual"));
		String documentId = uploadFile(token, "blank.png", "image/png", TestFiles.blankPng(), null);
		processor.processPendingDocuments();
		mvc.perform(get("/api/documents/" + documentId).header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.processingStatus").value("FAILED"));

		confirm(token, documentId, null, "Ceiling Fan", 24)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.document.processingStatus").value("CONFIRMED"));
	}

	/** Uploads an invoice PDF and lets the background reader read it (status PROCESSED). */
	private String uploadReadInvoice(String token) throws Exception {
		byte[] invoice = TestFiles.pdfWithText("TAX INVOICE", "Invoice No: ME/2026-27/006745",
				"Philips Air Fryer HD9252/90 1 8,999.00", "Grand Total Rs. 8,999.00", "2 Years Warranty");
		String documentId = uploadFile(token, "invoice.pdf", "application/pdf", invoice, null);
		processor.processPendingDocuments();
		return documentId;
	}

	/** What the review screen sends after "Confirm & Save". */
	private ResultActions confirm(String token, String documentId, String productId, String name, int warrantyMonths)
			throws Exception {
		String body = """
				{"documentType": "INVOICE", "productId": %s,
				 "product": {"name": "%s", "categoryId": "%s", "brand": "Philips", "model": "HD9252/90",
				             "purchaseDate": "%s", "purchasePrice": 8999, "currency": "INR",
				             "seller": "Metro Electronics", "invoiceNumber": "ME/2026-27/006745",
				             "warrantyMonths": %d}}
				""".formatted(productId == null ? "null" : "\"" + productId + "\"", name, categoryId(token, "kitchen"),
				BOUGHT, warrantyMonths);
		return mvc.perform(post("/api/documents/" + documentId + "/confirm")
				.header(AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}
}
