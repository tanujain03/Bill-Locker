package project.bill_locker.warranty;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.document.DocumentApiTestBase;

/** GET /api/warranties — "today" is 2026-10-07 (FixedClockConfig). */
class WarrantyApiTests extends DocumentApiTestBase {

	/** One saved bill with four products: expiring in 10 days, active, expired 36 days ago, no dates. */
	private static final String FOUR_ITEMS = """
			{"documentType": "INVOICE", "sellerName": "Croma", "purchaseDate": "2025-10-10", "items": [
			  {"productName": "Air conditioner", "warrantyEndDate": "2026-10-17", "warrantyProvider": "LG"},
			  {"productName": "Phone", "modelNumber": "SM-S931B", "warrantyEndDate": "2027-10-07", "warrantyProvider": "Samsung India"},
			  {"productName": "Kettle", "warrantyEndDate": "2026-09-01"},
			  {"productName": "Cable"}
			]}
			""";

	private ResultActions warranties(String token, String query) throws Exception {
		return mvc.perform(get("/api/warranties" + query).header("Authorization", bearer(token)));
	}

	@Test
	void onlySavedBillsCount() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-saved"));
		saveBill(token, FOUR_ITEMS);
		// An AI-read bill that was never saved: its products must not appear.
		String unsaved = uploadPdf(token, "read-only.pdf");
		mvc.perform(post("/api/documents/" + unsaved + "/extract").header("Authorization", bearer(token)))
				.andExpect(status().isOk());

		warranties(token, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.counts.all").value(4))
				.andExpect(jsonPath("$.items.length()").value(4));
	}

	@Test
	void statusesAndDaysLeft() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-status"));
		String id = saveBill(token, FOUR_ITEMS);

		warranties(token, "")
				.andExpect(jsonPath("$.items[0].productName").value("Air conditioner"))
				.andExpect(jsonPath("$.items[0].status").value("EXPIRING_SOON"))
				.andExpect(jsonPath("$.items[0].daysLeft").value(10))
				.andExpect(jsonPath("$.items[0].documentId").value(id))
				.andExpect(jsonPath("$.items[0].sellerName").value("Croma"))
				.andExpect(jsonPath("$.items[1].status").value("ACTIVE"))
				.andExpect(jsonPath("$.items[2].status").value("EXPIRED"))
				.andExpect(jsonPath("$.items[2].daysLeft").value(-36))
				.andExpect(jsonPath("$.items[3].status").value("NO_INFO"))
				.andExpect(jsonPath("$.items[3].daysLeft").doesNotExist())
				.andExpect(jsonPath("$.counts.active").value(1))
				.andExpect(jsonPath("$.counts.expiringSoon").value(1))
				.andExpect(jsonPath("$.counts.expired").value(1))
				.andExpect(jsonPath("$.counts.noInfo").value(1));
	}

	@Test
	void endWorkedOutFromStartAndMonths() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-computed"));
		saveBill(token, """
				{"items": [{"productName": "Fridge", "warrantyStartDate": "2026-01-10", "warrantyPeriodMonths": 12}]}
				""");

		warranties(token, "")
				.andExpect(jsonPath("$.items[0].endDate").value("2027-01-09"))
				.andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
	}

	@Test
	void statusFilterKeepsAllCounts() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-filter"));
		saveBill(token, FOUR_ITEMS);

		warranties(token, "?status=EXPIRED")
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].productName").value("Kettle"))
				.andExpect(jsonPath("$.counts.all").value(4))
				.andExpect(jsonPath("$.counts.expired").value(1));
	}

	@Test
	void searchFiltersCountsToo() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-search"));
		saveBill(token, FOUR_ITEMS);

		// "samsung" only matches the phone's warranty provider; case doesn't matter.
		warranties(token, "?q=SAMSUNG")
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].productName").value("Phone"))
				.andExpect(jsonPath("$.counts.all").value(1))
				.andExpect(jsonPath("$.counts.expired").value(0));
		// Seller and model match too.
		warranties(token, "?q=croma").andExpect(jsonPath("$.counts.all").value(4));
		warranties(token, "?q=s931").andExpect(jsonPath("$.items[0].productName").value("Phone"));
	}

	@Test
	void expiredMostRecentFirst() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-expired"));
		saveBill(token, """
				{"items": [
				  {"productName": "Old", "warrantyEndDate": "2026-01-01"},
				  {"productName": "Recent", "warrantyEndDate": "2026-09-30"}
				]}
				""");

		warranties(token, "?status=EXPIRED")
				.andExpect(jsonPath("$.items[*].productName", contains("Recent", "Old")));
	}

	@Test
	void activeSoonestFirst() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-active"));
		saveBill(token, """
				{"items": [
				  {"productName": "Later", "warrantyEndDate": "2028-01-01"},
				  {"productName": "Sooner", "warrantyEndDate": "2027-01-01"}
				]}
				""");

		warranties(token, "?status=ACTIVE")
				.andExpect(jsonPath("$.items[*].productName", contains("Sooner", "Later")));
	}

	@Test
	void badStatusIs400() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-bad"));
		warranties(token, "?status=BAD").andExpect(status().isBadRequest());
	}

	@Test
	void otherUsersBillsAbsent() throws Exception {
		String other = registerAndGetToken(uniqueEmail("w-other"));
		saveBill(other, FOUR_ITEMS);
		String me = registerAndGetToken(uniqueEmail("w-me"));

		warranties(me, "").andExpect(jsonPath("$.counts.all").value(0));
	}

	@Test
	void emptyAccount() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-empty"));

		warranties(token, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.counts.all").value(0))
				.andExpect(jsonPath("$.counts.noInfo").value(0))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void onlyInvoicesWarrantyCardsAndProductsWithWarrantyDetails() throws Exception {
		String token = registerAndGetToken(uniqueEmail("w-types"));
		// Shown: an invoice (even without warranty details) and a warranty card.
		saveBill(token, """
				{"documentType": "INVOICE", "items": [{"productName": "Mixer"}]}
				""");
		saveBill(token, """
				{"documentType": "WARRANTY_CARD", "items": [{"productName": "LG Split AC"}]}
				""");
		// A receipt with warranty details still counts…
		saveBill(token, """
				{"documentType": "RECEIPT", "items": [{"productName": "Trimmer", "warrantyPeriodMonths": 6}]}
				""");
		// …but a ride receipt, an "other" document and an untyped one without them don't.
		saveBill(token, """
				{"documentType": "RECEIPT", "items": [{"productName": "Bike ride"}]}
				""");
		saveBill(token, """
				{"documentType": "OTHER", "items": [{"productName": "Parking"}]}
				""");
		saveBill(token, """
				{"items": [{"productName": "Groceries"}]}
				""");

		warranties(token, "?q=")
				.andExpect(jsonPath("$.counts.all").value(3))
				.andExpect(jsonPath("$.items[*].productName").value(org.hamcrest.Matchers.containsInAnyOrder(
						"Mixer", "LG Split AC", "Trimmer")));
	}
}
