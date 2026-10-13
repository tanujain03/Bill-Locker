package project.bill_locker.warranty;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.document.DocumentApiTestBase;

/** GET /api/documents/{id}/items/{position}/claim-kit — "today" is 2026-10-07 (FixedClockConfig). */
class ClaimKitApiTests extends DocumentApiTestBase {

	/** Product 0 has every fact; product 1 has only a start date + months and no brand. */
	private static final String BILL = """
			{"documentType": "INVOICE", "documentNumber": "INV-2041", "sellerName": "Croma",
			 "sellerContact": "1800-123-4567", "purchaseDate": "2026-01-10", "totalAmount": 82000, "items": [
			  {"productName": "Galaxy S25", "brand": "Samsung", "modelNumber": "SM-S931B", "serialNumber": "R5CX12345",
			   "unitPrice": 79999, "warrantyEndDate": "2027-10-07", "warrantyProvider": "Samsung India"},
			  {"productName": "Electric Kettle", "warrantyStartDate": "2026-01-10", "warrantyPeriodMonths": 12}
			]}
			""";

	private ResultActions kit(String token, String id, int position) throws Exception {
		return mvc.perform(get("/api/documents/" + id + "/items/" + position + "/claim-kit").header("Authorization", bearer(token)));
	}

	@Test
	void aCompleteKit() throws Exception {
		String token = registerAndGetToken(uniqueEmail("kit-full"));
		String id = saveBill(token, BILL);

		kit(token, id, 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productName").value("Galaxy S25"))
				.andExpect(jsonPath("$.brand").value("Samsung"))
				.andExpect(jsonPath("$.serialNumber").value("R5CX12345"))
				.andExpect(jsonPath("$.invoiceNumber").value("INV-2041"))
				.andExpect(jsonPath("$.sellerContact").value("1800-123-4567"))
				.andExpect(jsonPath("$.purchaseDate").value("2026-01-10"))
				.andExpect(jsonPath("$.warrantyEndDate").value("2027-10-07"))
				.andExpect(jsonPath("$.daysLeft").value(365))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.supportSearchUrl").value(containsString("q=Samsung+customer+care+warranty+claim")))
				.andExpect(jsonPath("$.serviceCentreUrl").value(containsString("google.com/maps/search/?api=1&query=Samsung")))
				.andExpect(jsonPath("$.missing.length()").value(0));
	}

	@Test
	void missingFactsAndAWorkedOutEndDate() throws Exception {
		String token = registerAndGetToken(uniqueEmail("kit-missing"));
		String id = saveBill(token, BILL);

		kit(token, id, 1)
				.andExpect(jsonPath("$.position").value(1))
				.andExpect(jsonPath("$.warrantyEndDate").value("2027-01-09")) // 10 Jan 2026 + 12 months − 1 day
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				// No brand: the links search for the product name instead.
				.andExpect(jsonPath("$.supportSearchUrl").value(containsString("q=Electric+Kettle")))
				.andExpect(jsonPath("$.missing", contains("Model number", "Serial number")));
	}

	@Test
	void anExpiredWarrantyStillGetsAKit() throws Exception {
		String token = registerAndGetToken(uniqueEmail("kit-expired"));
		String id = saveBill(token, """
				{"items": [{"productName": "Mixer", "warrantyEndDate": "2026-09-01"}]}""");

		kit(token, id, 0)
				.andExpect(jsonPath("$.status").value("EXPIRED"))
				.andExpect(jsonPath("$.daysLeft").value(-36))
				.andExpect(jsonPath("$.missing", contains("Model number", "Serial number", "Purchase date", "Invoice number")));
	}

	@Test
	void someoneElsesProductOrAMissingPositionIsNotFound() throws Exception {
		String owner = registerAndGetToken(uniqueEmail("kit-owner"));
		String other = registerAndGetToken(uniqueEmail("kit-other"));
		String id = saveBill(owner, BILL);

		kit(other, id, 0).andExpect(status().isNotFound());
		kit(owner, id, 5).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
	}

	@Test
	void warrantyRowsSayWhichProductTheyAre() throws Exception {
		String token = registerAndGetToken(uniqueEmail("kit-position"));
		saveBill(token, BILL);

		// Sorted by end date: the kettle (Jan 2027) comes before the phone (Oct 2027).
		mvc.perform(get("/api/warranties").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.items[0].productName").value("Electric Kettle"))
				.andExpect(jsonPath("$.items[0].position").value(1))
				.andExpect(jsonPath("$.items[1].position").value(0));
	}
}
