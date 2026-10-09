package project.bill_locker.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.FakeDetailExtractor;
import project.bill_locker.document.DocumentApiTestBase;
import project.bill_locker.document.DocumentReadWorker;
import project.bill_locker.document.DocumentService;
import project.bill_locker.document.ai.ExtractionException;

/** GET /api/dashboard — "today" is 2026-10-07 (FixedClockConfig), so the 12 months are 2025-11 … 2026-10. */
class DashboardApiTests extends DocumentApiTestBase {

	@Autowired
	private DocumentService documentService;
	@Autowired
	private DocumentReadWorker reader;
	@Autowired
	private FakeDetailExtractor ai;

	@AfterEach
	void drainReadQueue() {
		// The read queue is shared by all tests: leave it empty for the next one.
		ai.reset();
		while (reader.runOnce()) {
			// drain
		}
	}

	private ResultActions dashboard(String token) throws Exception {
		return mvc.perform(get("/api/dashboard").header("Authorization", bearer(token)));
	}

	private UUID userId(String token) throws Exception {
		String body = mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	/** A saved bill with one product; null seller/date/total are left out. */
	private static String bill(String seller, String date, String total) {
		String json = "{\"items\": [{\"productName\": \"Thing\"}]";
		if (seller != null) {
			json += ", \"sellerName\": \"" + seller + "\"";
		}
		if (date != null) {
			json += ", \"purchaseDate\": \"" + date + "\"";
		}
		if (total != null) {
			json += ", \"totalAmount\": " + total;
		}
		return json + "}";
	}

	@Test
	void emptyAccountIsAllZeros() throws Exception {
		String token = registerAndGetToken(uniqueEmail("d-empty"));

		dashboard(token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.savedBills").value(0))
				.andExpect(jsonPath("$.totalSpent").value(0))
				.andExpect(jsonPath("$.spendingByMonth.length()").value(12))
				.andExpect(jsonPath("$.spendingByMonth[11].amount").value(0))
				.andExpect(jsonPath("$.topShops.length()").value(0))
				.andExpect(jsonPath("$.expiringSoon.length()").value(0))
				.andExpect(jsonPath("$.recentBills.length()").value(0))
				.andExpect(jsonPath("$.attention.toReview").value(0));
	}

	@Test
	void countsOnlySavedBills() throws Exception {
		String token = registerAndGetToken(uniqueEmail("d-saved"));
		saveBill(token, bill("Croma", "2026-09-01", "1000.00"));
		saveBill(token, bill("Croma", "2026-09-02", "2500.50"));
		String unsaved = uploadPdf(token, "ai-read.pdf"); // the fake AI "reads" a 1499.00 invoice
		mvc.perform(post("/api/documents/" + unsaved + "/extract").header("Authorization", bearer(token)))
				.andExpect(status().isOk());

		dashboard(token)
				.andExpect(jsonPath("$.savedBills").value(2))
				.andExpect(jsonPath("$.totalSpent").value(3500.50))
				.andExpect(jsonPath("$.attention.toReview").value(1));
	}

	@Test
	void attentionCounts() throws Exception {
		drainReadQueue(); // nothing from other tests may be in front of ours
		String token = registerAndGetToken(uniqueEmail("d-attention"));
		UUID user = userId(token);
		documentService.createFromBytes(user, "failed.pdf", pdfBytes(), "me@gmail.com");
		ai.willFail(new ExtractionException(ExtractionException.FAILED, "Gemini is busy right now."));
		reader.runOnce(); // → read failed
		ai.reset();
		documentService.createFromBytes(user, "waiting.pdf", pdfBytes(), "me@gmail.com");

		dashboard(token)
				.andExpect(jsonPath("$.attention.readFailed").value(1))
				.andExpect(jsonPath("$.attention.reading").value(1))
				.andExpect(jsonPath("$.attention.toReview").value(0));
	}

	@Test
	void spendingMonthsWindow() throws Exception {
		String token = registerAndGetToken(uniqueEmail("d-months"));
		saveBill(token, bill("Croma", "2026-09-15", "38200"));

		dashboard(token)
				.andExpect(jsonPath("$.spendingByMonth[0].month").value("2025-11"))
				.andExpect(jsonPath("$.spendingByMonth[11].month").value("2026-10"))
				.andExpect(jsonPath("$.spendingByMonth[10].month").value("2026-09"))
				.andExpect(jsonPath("$.spendingByMonth[10].amount").value(38200.00))
				.andExpect(jsonPath("$.spendingByMonth[10].bills").value(1))
				.andExpect(jsonPath("$.spendingByMonth[9].amount").value(0));
	}

	@Test
	void spendingOutsideWindowIsExcluded() throws Exception {
		String token = registerAndGetToken(uniqueEmail("d-outside"));
		saveBill(token, bill("Old shop", "2025-10-31", "100"));
		saveBill(token, bill("Future shop", "2026-11-01", "200"));

		ResultActions result = dashboard(token).andExpect(jsonPath("$.totalSpent").value(300.00));
		for (int i = 0; i < 12; i++) {
			result.andExpect(jsonPath("$.spendingByMonth[" + i + "].bills").value(0));
		}
	}

	@Test
	void billWithoutDateCountsInTotalNotChart() throws Exception {
		String token = registerAndGetToken(uniqueEmail("d-nodate"));
		saveBill(token, bill("Croma", null, "500"));
		saveBill(token, bill("Croma", "2026-10-01", null));

		dashboard(token)
				.andExpect(jsonPath("$.totalSpent").value(500.00))
				.andExpect(jsonPath("$.billsWithoutDateOrTotal").value(2))
				// The bill with a date but no total sits in its month without adding money.
				.andExpect(jsonPath("$.spendingByMonth[11].bills").value(1))
				.andExpect(jsonPath("$.spendingByMonth[11].amount").value(0));
	}

	@Test
	void monthBillCountMatchesTheMonthList() throws Exception {
		// The bar links to "saved bills bought that month", which includes a bill with no total;
		// so the bar's bill count includes it too (it just adds nothing to the amount).
		String token = registerAndGetToken(uniqueEmail("d-month-count"));
		saveBill(token, bill("Croma", "2026-09-10", "100"));
		saveBill(token, bill("Croma", "2026-09-20", null));

		dashboard(token)
				.andExpect(jsonPath("$.spendingByMonth[10].bills").value(2))
				.andExpect(jsonPath("$.spendingByMonth[10].amount").value(100.00));
	}

	@Test
	void thisMonthNumbers() throws Exception {
		String token = registerAndGetToken(uniqueEmail("d-month"));
		saveBill(token, bill("Croma", "2026-10-02", "750"));
		saveBill(token, bill("Croma", "2026-09-30", "100"));

		dashboard(token)
				.andExpect(jsonPath("$.savedBillsThisMonth").value(1))
				.andExpect(jsonPath("$.spentThisMonth").value(750.00));
	}

	@Test
	void topShopsGroupedCaseInsensitive() throws Exception {
		String token = registerAndGetToken(uniqueEmail("d-shops"));
		saveBill(token, bill("Croma ", "2026-01-01", "100"));
		saveBill(token, bill("croma", "2026-02-01", "200")); // the latest spelling wins
		saveBill(token, bill("Amazon", "2026-03-01", "250"));

		dashboard(token)
				.andExpect(jsonPath("$.topShops.length()").value(2))
				.andExpect(jsonPath("$.topShops[0].name").value("croma"))
				.andExpect(jsonPath("$.topShops[0].amount").value(300.00))
				.andExpect(jsonPath("$.topShops[0].bills").value(2))
				.andExpect(jsonPath("$.topShops[1].name").value("Amazon"));
	}

	@Test
	void productsAndWarranties() throws Exception {
		String token = registerAndGetToken(uniqueEmail("d-warranty"));
		saveBill(token, """
				{"documentType": "INVOICE", "sellerName": "Croma", "items": [
				  {"productName": "Phone", "warrantyEndDate": "2028-01-01"},
				  {"productName": "AC", "warrantyEndDate": "2026-10-17"},
				  {"productName": "Cable"}
				]}
				""");

		dashboard(token)
				.andExpect(jsonPath("$.products").value(3))
				.andExpect(jsonPath("$.productsWithWarranty").value(2))
				.andExpect(jsonPath("$.warranties.active").value(1))
				.andExpect(jsonPath("$.warranties.expiringSoon").value(1))
				.andExpect(jsonPath("$.warranties.noInfo").value(1))
				.andExpect(jsonPath("$.expiringSoon.length()").value(1))
				.andExpect(jsonPath("$.expiringSoon[0].productName").value("AC"))
				.andExpect(jsonPath("$.expiringSoon[0].daysLeft").value(10));
	}

	@Test
	void recentBillsAnyStatusNewestFirst() throws Exception {
		String token = registerAndGetToken(uniqueEmail("d-recent"));
		for (int i = 1; i <= 6; i++) {
			uploadPdf(token, "bill-" + i + ".pdf");
		}

		dashboard(token)
				.andExpect(jsonPath("$.recentBills.length()").value(5))
				.andExpect(jsonPath("$.recentBills[0].fileName").value("bill-6.pdf"))
				.andExpect(jsonPath("$.recentBills[0].status").value("UPLOADED"));
	}

	@Test
	void otherUsersDataAbsent() throws Exception {
		String other = registerAndGetToken(uniqueEmail("d-other"));
		saveBill(other, bill("Croma", "2026-09-01", "999"));
		String me = registerAndGetToken(uniqueEmail("d-me"));

		dashboard(me)
				.andExpect(jsonPath("$.savedBills").value(0))
				.andExpect(jsonPath("$.recentBills.length()").value(0))
				.andExpect(jsonPath("$.topShops.length()").value(0));
	}
}
