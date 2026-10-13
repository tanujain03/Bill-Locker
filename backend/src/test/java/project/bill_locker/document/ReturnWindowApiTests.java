package project.bill_locker.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Return windows (task 14) — "today" is 2026-10-07 (FixedClockConfig). */
class ReturnWindowApiTests extends DocumentApiTestBase {

	@Test
	void aReturnDateIsKeptAndShown() throws Exception {
		String token = registerAndGetToken(uniqueEmail("return-save"));
		String id = saveBill(token, """
				{"sellerName": "Myntra", "purchaseDate": "2026-10-03", "returnByDate": "2026-10-17",
				 "items": [{"productName": "Denim jacket"}]}""");

		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.returnByDate").value("2026-10-17"));
		mvc.perform(get("/api/documents").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$[0].returnByDate").value("2026-10-17"));
	}

	@Test
	void theDashboardListsOnlyOpenReturnsSoonestFirst() throws Exception {
		String token = registerAndGetToken(uniqueEmail("return-dashboard"));
		saveBill(token, """
				{"sellerName": "Myntra", "purchaseDate": "2026-10-03", "returnByDate": "2026-10-17",
				 "items": [{"productName": "Denim jacket"}]}""");
		saveBill(token, """
				{"sellerName": "Amazon", "purchaseDate": "2026-10-01", "returnByDate": "2026-10-07",
				 "items": [{"productName": "Kettle"}]}"""); // today is the last day: still open
		saveBill(token, """
				{"sellerName": "Ajio", "purchaseDate": "2026-09-01", "returnByDate": "2026-09-15"}"""); // closed
		saveBill(token, """
				{"sellerName": "Croma", "purchaseDate": "2026-10-01"}"""); // no return date

		mvc.perform(get("/api/dashboard").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.openReturns.length()").value(2))
				.andExpect(jsonPath("$.openReturns[0].sellerName").value("Amazon"))
				.andExpect(jsonPath("$.openReturns[0].productName").value("Kettle"))
				.andExpect(jsonPath("$.openReturns[0].daysLeft").value(0))
				.andExpect(jsonPath("$.openReturns[1].sellerName").value("Myntra"))
				.andExpect(jsonPath("$.openReturns[1].daysLeft").value(10));
	}

	@Test
	void aReturnDateBeforeThePurchaseIsRejected() throws Exception {
		String token = registerAndGetToken(uniqueEmail("return-invalid"));
		String id = uploadPdf(token, "bill.pdf");

		mvc.perform(put("/api/documents/" + id).header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"purchaseDate": "2026-10-03", "returnByDate": "2026-10-01"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.returnByDate").value("Must be on or after the purchase date."));
	}

	@Test
	void theSampleDataHasAnOpenReturn() throws Exception {
		String token = registerAndGetToken(uniqueEmail("return-sample"));
		mvc.perform(post("/api/sample-data").header("Authorization", bearer(token))).andExpect(status().isOk());

		mvc.perform(get("/api/dashboard").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.openReturns.length()").value(1))
				.andExpect(jsonPath("$.openReturns[0].productName").value("Philips HD9252 Air Fryer"))
				.andExpect(jsonPath("$.openReturns[0].daysLeft").value(5));
	}
}
