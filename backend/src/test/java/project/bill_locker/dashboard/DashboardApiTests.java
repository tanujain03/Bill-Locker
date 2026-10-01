package project.bill_locker.dashboard;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import project.bill_locker.ApiTest;
import project.bill_locker.TestFiles;
import project.bill_locker.processing.DocumentProcessor;

/** The dashboard's numbers, worked out from the user's products and documents. */
class DashboardApiTests extends ApiTest {

	@Autowired
	private DocumentProcessor processor;

	@Test
	void summarisesProductsWarrantiesAndDocuments() throws Exception {
		String token = registerAndGetToken(uniqueEmail("dashboard"));
		LocalDate today = LocalDate.now();
		createProduct(token, """
				{"name": "Dell Laptop", "categoryId": "%s", "purchasePrice": 62990, "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(categoryId(token, "computers"), today.minusMonths(12).plusDays(10))); // ends in about 10 days
		createProduct(token, """
				{"name": "LG Refrigerator", "categoryId": "%s", "purchasePrice": 30000, "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(categoryId(token, "home-appliances"), today.minusYears(3)));
		createProduct(token, """
				{"name": "Sony Headphones", "categoryId": "%s", "purchasePrice": 4990, "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(categoryId(token, "audio"), today.minusMonths(1)));
		createProduct(token, """
				{"name": "Old Chair", "purchasePrice": 1000}
				""");
		uploadFile(token, "invoice.pdf", "application/pdf",
				TestFiles.pdfWithText("TAX INVOICE", "Invoice No: ME/2026-27/006745", "Grand Total Rs. 8,999.00"), null);
		uploadFile(token, "blank.png", "image/png", TestFiles.blankPng(), null);
		processor.processPendingDocuments(); // the invoice is read (to review), the blank photo fails

		mvc.perform(get("/api/dashboard/summary").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalProducts").value(4))
				.andExpect(jsonPath("$.totalDocuments").value(2))
				.andExpect(jsonPath("$.documentsToReview").value(1))
				.andExpect(jsonPath("$.totalSpending").value(98980.0))
				.andExpect(jsonPath("$.currency").value("INR"))
				.andExpect(jsonPath("$.warranties.total").value(4))
				.andExpect(jsonPath("$.warranties.active").value(1))
				.andExpect(jsonPath("$.warranties.expiringSoon").value(1))
				.andExpect(jsonPath("$.warranties.expired").value(1))
				.andExpect(jsonPath("$.warranties.unknown").value(1))
				.andExpect(jsonPath("$.spendingByCategory[*].categoryName",
						contains("Computers & Accessories", "Home Appliances", "Audio & Wearables", "Other")))
				.andExpect(jsonPath("$.spendingByCategory[0].categorySlug").value("computers"))
				.andExpect(jsonPath("$.spendingByCategory[0].amount").value(62990.0))
				.andExpect(jsonPath("$.upcomingExpirations[*].productName", contains("Dell Laptop")))
				.andExpect(jsonPath("$.upcomingExpirations[0].status").value("EXPIRING_SOON"))
				.andExpect(jsonPath("$.upcomingServices", empty()))
				.andExpect(jsonPath("$.recentDocuments[*].fileName", contains("blank.png", "invoice.pdf")));
	}

	@Test
	void aNewAccountStartsEmpty() throws Exception {
		String token = registerAndGetToken(uniqueEmail("empty"));

		mvc.perform(get("/api/dashboard/summary").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalProducts").value(0))
				.andExpect(jsonPath("$.totalDocuments").value(0))
				.andExpect(jsonPath("$.totalSpending").value(0))
				.andExpect(jsonPath("$.warranties.total").value(0))
				.andExpect(jsonPath("$.spendingByCategory", empty()))
				.andExpect(jsonPath("$.upcomingExpirations", hasSize(0)))
				.andExpect(jsonPath("$.recentDocuments", empty()));
	}
}
