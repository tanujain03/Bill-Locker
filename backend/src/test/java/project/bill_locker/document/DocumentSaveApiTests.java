package project.bill_locker.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Saving (and later editing) the reviewed details of a document. */
class DocumentSaveApiTests extends DocumentApiTestBase {

	private static final String TWO_ITEMS = """
			{
			  "documentType": "INVOICE", "documentNumber": "INV-77",
			  "sellerName": "Croma", "sellerAddress": "Mumbai", "sellerContact": "1800-123",
			  "buyerName": "Asha", "buyerAddress": "Pune", "buyerEmail": "asha@example.com",
			  "purchaseDate": "2026-03-01", "taxAmount": 228.66, "totalAmount": 1499.00,
			  "items": [
			    {"productName": "Phone", "serialNumber": "SN-1", "unitPrice": 1199,
			     "warrantyPeriodMonths": 12, "warrantyStartDate": "2026-03-01", "warrantyEndDate": "2027-02-28",
			     "warrantyProvider": "Samsung"},
			    {"productName": "Charger"}
			  ]
			}
			""";

	private ResultActions save(String token, String id, String json) throws Exception {
		return mvc.perform(put("/api/documents/" + id).header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void saveStoresDetailsAndItems() throws Exception {
		String token = registerAndGetToken(uniqueEmail("save"));
		String id = uploadPdf(token, "bill.pdf");

		save(token, id, TWO_ITEMS)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SAVED"))
				.andExpect(jsonPath("$.documentNumber").value("INV-77"))
				.andExpect(jsonPath("$.totalAmount").value(1499.0))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[1].productName").value("Charger"));

		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.sellerName").value("Croma"))
				.andExpect(jsonPath("$.purchaseDate").value("2026-03-01"))
				.andExpect(jsonPath("$.items[0].serialNumber").value("SN-1"))
				.andExpect(jsonPath("$.items[0].warrantyEndDate").value("2027-02-28"));
	}

	@Test
	void saveReplacesItems() throws Exception {
		String token = registerAndGetToken(uniqueEmail("replace"));
		String id = uploadPdf(token, "bill.pdf");
		save(token, id, TWO_ITEMS).andExpect(status().isOk());

		save(token, id, """
				{"sellerName": "Croma", "items": [{"productName": "Laptop"}]}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].productName").value("Laptop"))
				.andExpect(jsonPath("$.documentNumber").doesNotExist());
	}

	@Test
	void saveRejectsBadValues() throws Exception {
		String token = registerAndGetToken(uniqueEmail("bad"));
		String id = uploadPdf(token, "bill.pdf");

		save(token, id, """
				{"buyerEmail": "x", "totalAmount": -1,
				 "items": [{"warrantyPeriodMonths": 700}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.buyerEmail").exists())
				.andExpect(jsonPath("$.fieldErrors.totalAmount").exists())
				.andExpect(jsonPath("$.fieldErrors['items[0].warrantyPeriodMonths']").exists());
	}

	@Test
	void saveRejectsAmountsTooBigToStore() throws Exception {
		String token = registerAndGetToken(uniqueEmail("huge"));
		String id = uploadPdf(token, "bill.pdf");

		save(token, id, """
				{"totalAmount": 10000000000000, "taxAmount": 1.234, "items": [{"unitPrice": 1e15}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.totalAmount").exists())
				.andExpect(jsonPath("$.fieldErrors.taxAmount").exists())
				.andExpect(jsonPath("$.fieldErrors['items[0].unitPrice']").exists());
	}

	@Test
	void saveRejectsWarrantyEndBeforeStart() throws Exception {
		String token = registerAndGetToken(uniqueEmail("dates"));
		String id = uploadPdf(token, "bill.pdf");

		save(token, id, """
				{"items": [{"productName": "Phone",
				            "warrantyStartDate": "2026-03-01", "warrantyEndDate": "2026-01-01"}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors['items[0].warrantyEndDate']").exists());
	}

	@Test
	void searchFindsSellerAndProduct() throws Exception {
		String token = registerAndGetToken(uniqueEmail("find"));
		String id = uploadPdf(token, "scan.pdf");
		uploadPdf(token, "other.pdf");
		save(token, id, TWO_ITEMS).andExpect(status().isOk());

		for (String query : new String[] {"q=croma", "q=PHONE", "q=inv-77", "type=INVOICE", "status=SAVED"}) {
			mvc.perform(get("/api/documents?" + query).header("Authorization", bearer(token)))
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].id").value(id))
					.andExpect(jsonPath("$[0].itemCount").value(2))
					.andExpect(jsonPath("$[0].firstProductName").value("Phone"));
		}
	}

	@Test
	void otherUserCannotSave() throws Exception {
		String owner = registerAndGetToken(uniqueEmail("owner"));
		String other = registerAndGetToken(uniqueEmail("other"));
		String id = uploadPdf(owner, "bill.pdf");

		save(other, id, TWO_ITEMS)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
	}
}
