package project.bill_locker.product;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.document.DocumentApiTestBase;

/** GET /api/documents/{id}/items/{position}/product — "today" is 2026-10-07 (FixedClockConfig). */
class ProductApiTests extends DocumentApiTestBase {

	private static final String PHONE_BILL = """
			{"documentType": "INVOICE", "category": "ELECTRONICS", "documentNumber": "INV-1", "sellerName": "Croma",
			 "purchaseDate": "2026-01-07", "totalAmount": 79999, "items": [
			  {"productName": "Galaxy S25", "brand": "Samsung", "modelNumber": "SM-S931B", "serialNumber": "R5CX1",
			   "unitPrice": 79999, "warrantyPeriodMonths": 12, "warrantyStartDate": "2026-01-07", "warrantyProvider": "Samsung India"}
			]}
			""";

	private ResultActions product(String token, String id, int position) throws Exception {
		return mvc.perform(get("/api/documents/" + id + "/items/" + position + "/product").header("Authorization", bearer(token)));
	}

	@Test
	void everythingAboutOneProduct() throws Exception {
		String token = registerAndGetToken(uniqueEmail("product-full"));
		String id = saveBill(token, PHONE_BILL);

		product(token, id, 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productName").value("Galaxy S25"))
				.andExpect(jsonPath("$.brand").value("Samsung"))
				.andExpect(jsonPath("$.category").value("ELECTRONICS"))
				.andExpect(jsonPath("$.price").value(79999.00))
				.andExpect(jsonPath("$.sellerName").value("Croma"))
				.andExpect(jsonPath("$.warrantyMonths").value(12))
				.andExpect(jsonPath("$.warrantyEndDate").value("2027-01-06")) // start + 12 months − 1 day
				.andExpect(jsonPath("$.daysLeft").value(91))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.ownedDays").value(273))
				.andExpect(jsonPath("$.costPerMonth").value(8888.78)) // 79,999 over 9 months
				.andExpect(jsonPath("$.bill.id").value(id))
				.andExpect(jsonPath("$.related.length()").value(0))
				.andExpect(jsonPath("$.missing.length()").value(0));
	}

	@Test
	void relatedDocumentsBySerialOrModelNumberOnly() throws Exception {
		String token = registerAndGetToken(uniqueEmail("product-related"));
		String other = registerAndGetToken(uniqueEmail("product-related-other"));
		String id = saveBill(token, PHONE_BILL);
		// The warranty card of this very phone: same serial, written differently.
		String card = saveBill(token, """
				{"documentType": "WARRANTY_CARD", "items": [{"productName": "Phone", "serialNumber": " r5cx1 "}]}""");
		// Another phone of the same model.
		String sameModel = saveBill(token, """
				{"items": [{"productName": "Galaxy S25", "modelNumber": "sm-s931b", "serialNumber": "OTHER9"}]}""");
		saveBill(token, """
				{"items": [{"productName": "Kettle", "modelNumber": "K-1", "serialNumber": "K99"}]}""");
		// Someone else's document never shows up.
		saveBill(other, """
				{"items": [{"productName": "Phone", "serialNumber": "R5CX1"}]}""");

		product(token, id, 0)
				.andExpect(jsonPath("$.related.length()").value(2))
				.andExpect(jsonPath("$.related[?(@.document.id == '" + card + "')].matchedBy", contains("serial number")))
				.andExpect(jsonPath("$.related[?(@.document.id == '" + sameModel + "')].matchedBy", contains("model number")));
	}

	@Test
	void missingFactsAndABoughtThisMonthPrice() throws Exception {
		String token = registerAndGetToken(uniqueEmail("product-missing"));
		String id = saveBill(token, """
				{"purchaseDate": "2026-10-02", "items": [{"productName": "Mixer", "unitPrice": 2400}]}""");

		product(token, id, 0)
				.andExpect(jsonPath("$.ownedDays").value(5))
				.andExpect(jsonPath("$.costPerMonth").value(2400.00)) // under a month: the full price
				.andExpect(jsonPath("$.status").value("NO_INFO"))
				.andExpect(jsonPath("$.related.length()").value(0)) // no serial or model: nothing to match on
				.andExpect(jsonPath("$.missing", contains("Model number", "Serial number", "Warranty end date")));
	}

	@Test
	void someoneElsesProductOrAMissingPositionIsNotFound() throws Exception {
		String owner = registerAndGetToken(uniqueEmail("product-owner"));
		String other = registerAndGetToken(uniqueEmail("product-other"));
		String id = saveBill(owner, PHONE_BILL);

		product(other, id, 0).andExpect(status().isNotFound());
		product(owner, id, 3).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
	}
}
