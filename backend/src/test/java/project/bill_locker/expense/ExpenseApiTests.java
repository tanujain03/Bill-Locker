package project.bill_locker.expense;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.document.DocumentApiTestBase;

/** GET /api/expenses and PUT /api/expenses/budget — "today" is 2026-10-07 (FixedClockConfig). */
class ExpenseApiTests extends DocumentApiTestBase {

	/** {@code range} is the query string ("from=…&to=…"), or null for the default (this month). */
	private ResultActions report(String token, String range) throws Exception {
		return mvc.perform(get("/api/expenses" + (range == null ? "" : "?" + range))
				.header("Authorization", bearer(token)));
	}

	private ResultActions budget(String token, String json) throws Exception {
		return mvc.perform(put("/api/expenses/budget").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void emptyAccountShowsTheCurrentMonth() throws Exception {
		String token = registerAndGetToken(uniqueEmail("e-empty"));

		report(token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").value("2026-10-01"))
				.andExpect(jsonPath("$.to").value("2026-10-31"))
				.andExpect(jsonPath("$.previousFrom").value("2026-09-01"))
				.andExpect(jsonPath("$.previousTo").value("2026-09-30"))
				.andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.bills").value(0))
				.andExpect(jsonPath("$.days.length()").value(31))
				.andExpect(jsonPath("$.months.length()").value(12))
				.andExpect(jsonPath("$.months[11].month").value("2026-10"))
				.andExpect(jsonPath("$.categories.length()").value(0))
				.andExpect(jsonPath("$.budget").doesNotExist())
				.andExpect(jsonPath("$.biggestBill").doesNotExist());
	}

	@Test
	void aPastMonthByCategoryProductShopAndDay() throws Exception {
		String token = registerAndGetToken(uniqueEmail("e-month"));
		saveBill(token, """
				{"documentType": "RECEIPT", "category": "FOOD", "sellerName": "Swiggy",
				 "purchaseDate": "2026-09-05", "totalAmount": 500}""");
		saveBill(token, """
				{"documentType": "INVOICE", "category": "ELECTRONICS", "sellerName": "Croma",
				 "purchaseDate": "2026-09-20", "totalAmount": 30000,
				 "items": [{"productName": "Phone", "unitPrice": 28000}, {"productName": "Cover", "unitPrice": 500}]}""");
		saveBill(token, """
				{"sellerName": "Corner shop", "purchaseDate": "2026-09-20", "totalAmount": 200}""");
		saveBill(token, """
				{"documentType": "RECEIPT", "category": "FOOD", "sellerName": "Zomato",
				 "purchaseDate": "2026-08-10", "totalAmount": 300}""");
		// Read by the AI but not saved yet: not counted.
		String unsaved = uploadPdf(token, "ai-read.pdf");
		mvc.perform(post("/api/documents/" + unsaved + "/extract").header("Authorization", bearer(token)))
				.andExpect(status().isOk());

		report(token, "from=2026-09-01&to=2026-09-30")
				.andExpect(jsonPath("$.previousFrom").value("2026-08-01"))
				.andExpect(jsonPath("$.total").value(30700.00))
				.andExpect(jsonPath("$.bills").value(3))
				.andExpect(jsonPath("$.previousTotal").value(300.00))
				.andExpect(jsonPath("$.dailyAverage").value(1023.33)) // 30700 ÷ 30 days
				.andExpect(jsonPath("$.projectedTotal").doesNotExist()) // only for the current month
				.andExpect(jsonPath("$.categories.length()").value(3))
				.andExpect(jsonPath("$.categories[0].category").value("ELECTRONICS"))
				.andExpect(jsonPath("$.categories[0].amount").value(30000.00))
				.andExpect(jsonPath("$.categories[1].category").value("FOOD"))
				.andExpect(jsonPath("$.categories[1].previousAmount").value(300.00))
				.andExpect(jsonPath("$.categories[2].category").doesNotExist()) // not sorted yet
				.andExpect(jsonPath("$.uncategorizedBills").value(1))
				.andExpect(jsonPath("$.topProducts[0].name").value("Phone"))
				.andExpect(jsonPath("$.topProducts[0].amount").value(28000.00))
				.andExpect(jsonPath("$.topProducts[0].category").value("ELECTRONICS"))
				.andExpect(jsonPath("$.topShops[0].name").value("Croma"))
				.andExpect(jsonPath("$.days.length()").value(30))
				.andExpect(jsonPath("$.days[19].amount").value(30200.00))
				.andExpect(jsonPath("$.days[19].bills").value(2))
				.andExpect(jsonPath("$.months[11].month").value("2026-09"))
				.andExpect(jsonPath("$.months[10].amount").value(300.00))
				.andExpect(jsonPath("$.biggestBill.sellerName").value("Croma"));
	}

	@Test
	void theCurrentMonthIsProjected() throws Exception {
		String token = registerAndGetToken(uniqueEmail("e-pace"));
		saveBill(token, """
				{"documentType": "RECEIPT", "category": "FUEL", "purchaseDate": "2026-10-02", "totalAmount": 700}""");

		report(token, null)
				.andExpect(jsonPath("$.dailyAverage").value(100.00)) // 700 ÷ 7 days so far
				.andExpect(jsonPath("$.projectedTotal").value(3100.00)); // 100 × 31 days
	}

	@Test
	void lastSixMonthsComparesWithTheSixBefore() throws Exception {
		String token = registerAndGetToken(uniqueEmail("e-six"));
		saveBill(token, """
				{"category": "FOOD", "purchaseDate": "2026-05-03", "totalAmount": 100}""");
		saveBill(token, """
				{"category": "FOOD", "purchaseDate": "2026-10-01", "totalAmount": 200}""");
		saveBill(token, """
				{"category": "FOOD", "purchaseDate": "2026-04-30", "totalAmount": 50}""");

		// "Last 6 months" on the page: 1 May … today (7 Oct). Not whole months, so the period
		// before is the same number of days: 160 days ending on 30 Apr.
		report(token, "from=2026-05-01&to=2026-10-07")
				.andExpect(jsonPath("$.total").value(300.00))
				.andExpect(jsonPath("$.bills").value(2))
				.andExpect(jsonPath("$.previousTo").value("2026-04-30"))
				.andExpect(jsonPath("$.previousFrom").value("2025-11-22"))
				.andExpect(jsonPath("$.previousTotal").value(50.00))
				.andExpect(jsonPath("$.dailyAverage").value(1.88)) // 300 ÷ 160 days
				.andExpect(jsonPath("$.projectedTotal").doesNotExist()) // ends today: nothing to project
				.andExpect(jsonPath("$.days.length()").value(0)) // too long for a calendar
				.andExpect(jsonPath("$.months.length()").value(12))
				.andExpect(jsonPath("$.months[11].month").value("2026-10"));
	}

	@Test
	void aCustomRangeOfSomeDays() throws Exception {
		String token = registerAndGetToken(uniqueEmail("e-custom"));
		saveBill(token, """
				{"category": "TRAVEL", "purchaseDate": "2026-09-10", "totalAmount": 300}""");
		saveBill(token, """
				{"category": "TRAVEL", "purchaseDate": "2026-09-16", "totalAmount": 999}""");

		report(token, "from=2026-09-10&to=2026-09-15")
				.andExpect(jsonPath("$.total").value(300.00))
				.andExpect(jsonPath("$.days.length()").value(6))
				.andExpect(jsonPath("$.days[0].date").value("2026-09-10"))
				.andExpect(jsonPath("$.previousFrom").value("2026-09-04"))
				.andExpect(jsonPath("$.previousTo").value("2026-09-09"));
	}

	@Test
	void badRangesAreRejected() throws Exception {
		String token = registerAndGetToken(uniqueEmail("e-bad"));

		report(token, "from=september&to=2026-09-30")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_DATE"));
		report(token, "from=2026-09-30&to=2026-09-01")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RANGE"));
		report(token, "from=2020-01-01&to=2026-09-01")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("RANGE_TOO_LONG"));
	}

	@Test
	void budgetCanBeSetAndRemoved() throws Exception {
		String token = registerAndGetToken(uniqueEmail("e-budget"));

		budget(token, "{\"amount\": 20000}").andExpect(status().isOk());
		report(token, null).andExpect(jsonPath("$.budget").value(20000.00));

		budget(token, "{\"amount\": -5}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.amount").exists());

		budget(token, "{\"amount\": null}").andExpect(status().isOk());
		report(token, null).andExpect(jsonPath("$.budget").doesNotExist());
	}

	@Test
	void needsSignIn() throws Exception {
		mvc.perform(get("/api/expenses")).andExpect(status().isUnauthorized());
	}
}
