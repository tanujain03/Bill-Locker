package project.bill_locker.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import project.bill_locker.FakeDetailExtractor;

/** Bills and receipts: saved straight after the AI read, with a category; invoices still wait for review. */
class BillsAndReceiptsApiTests extends DocumentApiTestBase {

	@Autowired
	FakeDetailExtractor ai;
	@Autowired
	DocumentService documentService;
	@Autowired
	DocumentReadWorker worker;

	@AfterEach
	void resetAi() {
		ai.reset();
		while (worker.runOnce()) {
			// leave nothing queued for other tests
		}
	}

	private static DocumentDetails rideReceipt() {
		return new DocumentDetails(DocumentType.RECEIPT, "RD-1790", "Rapido", null, null, null, null, null,
				LocalDate.of(2026, 9, 25), null, new BigDecimal("47.00"), List.of(), BillCategory.TRAVEL);
	}

	@Test
	void aReadReceiptIsSavedStraightAwayAndNeedsNoReview() throws Exception {
		String token = registerAndGetToken(uniqueEmail("receipt"));
		String id = uploadPdf(token, "ride.pdf");
		ai.willReturn(rideReceipt());

		mvc.perform(post("/api/documents/" + id + "/extract").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SAVED"))
				.andExpect(jsonPath("$.category").value("TRAVEL"));

		mvc.perform(get("/api/documents?type=RECEIPT").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$[0].category").value("TRAVEL"))
				.andExpect(jsonPath("$[0].totalAmount").value(47.0));
		// Nothing to review, and it counts in the spending right away.
		mvc.perform(get("/api/dashboard").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.attention.toReview").value(0))
				.andExpect(jsonPath("$.savedBills").value(1));
	}

	@Test
	void aReadInvoiceStillWaitsForReview() throws Exception {
		String token = registerAndGetToken(uniqueEmail("invoice"));
		String id = uploadPdf(token, "tv.pdf");
		mvc.perform(post("/api/documents/" + id + "/extract").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.status").value("EXTRACTED"))
				.andExpect(jsonPath("$.category").doesNotExist());
	}

	@Test
	void theBackgroundReadSavesReceiptsToo() throws Exception {
		String token = registerAndGetToken(uniqueEmail("gmail-receipt"));
		String me = JsonPath.read(mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
				.andReturn().getResponse().getContentAsString(), "$.id");
		String id = documentService.createFromBytes(UUID.fromString(me), "swiggy.pdf", pdfBytes(), "me@gmail.com")
				.id().toString();
		ai.willReturn(rideReceipt());
		while (worker.runOnce()) {
			// read everything queued
		}

		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.status").value("SAVED"))
				.andExpect(jsonPath("$.readQueued").value(false));
	}

	@Test
	void theCategoryIsKeptOnlyForBillsAndReceipts() throws Exception {
		String token = registerAndGetToken(uniqueEmail("category"));
		String id = uploadPdf(token, "bill.pdf");

		save(token, id, """
				{"documentType": "RECEIPT", "sellerName": "Swiggy", "category": "FOOD"}
				""").andExpect(jsonPath("$.category").value("FOOD"));
		// Turned into an invoice: an invoice has no category.
		save(token, id, """
				{"documentType": "INVOICE", "sellerName": "Croma", "category": "FOOD"}
				""").andExpect(jsonPath("$.category").doesNotExist());
	}

	private org.springframework.test.web.servlet.ResultActions save(String token, String id, String json) throws Exception {
		return mvc.perform(put("/api/documents/" + id).header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
	}
}
