package project.bill_locker.search;

import static org.hamcrest.Matchers.contains;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.ApiTest;

/** Plain-English questions answered from the user's own products. */
class SearchApiTests extends ApiTest {

	@Test
	void answersQuestionsAboutTheUsersProducts() throws Exception {
		String token = registerAndGetToken(uniqueEmail("search"));
		LocalDate today = LocalDate.now();
		createProduct(token, """
				{"name": "Inspiron 15 Laptop", "brand": "Dell", "seller": "Croma Retail", "purchasePrice": 62990,
				 "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(today.minusMonths(12).plusDays(10)));
		createProduct(token, """
				{"name": "Double Door Refrigerator", "brand": "LG", "seller": "Reliance Digital", "purchasePrice": 30000,
				 "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(today.minusYears(3)));
		createProduct(token, """
				{"name": "WH-1000XM5 Headphones", "brand": "Sony", "purchasePrice": 4990, "purchaseDate": "%s",
				 "warrantyMonths": 12}
				""".formatted(today.minusMonths(1)));

		search(token, "Warranties expiring within 90 days")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.query").value("Warranties expiring within 90 days"))
				.andExpect(jsonPath("$.filters.daysUntilExpiry").value(90))
				.andExpect(jsonPath("$.filters.brand").doesNotExist()) // filters that weren't used are left out
				.andExpect(jsonPath("$.explanation").value(
						"Showing products whose warranty expires within the next 90 days, soonest expiry first."))
				.andExpect(jsonPath("$.results[*].name", contains("Inspiron 15 Laptop")));
		search(token, "Most expensive purchase")
				.andExpect(jsonPath("$.results[*].name", contains("Inspiron 15 Laptop")));
		search(token, "What did I buy from Reliance?")
				.andExpect(jsonPath("$.filters.seller").value("Reliance"))
				.andExpect(jsonPath("$.results[*].name", contains("Double Door Refrigerator")));
		search(token, "Expired warranties")
				.andExpect(jsonPath("$.results[*].name", contains("Double Door Refrigerator")));
		search(token, "my fridge")
				.andExpect(jsonPath("$.results[*].name", contains("Double Door Refrigerator")));
		search(token, "headphones under 5000")
				.andExpect(jsonPath("$.results[*].name", contains("WH-1000XM5 Headphones")));
	}

	@Test
	void anEmptyQuestionIsRefused() throws Exception {
		String token = registerAndGetToken(uniqueEmail("search-empty"));

		search(token, " ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.query").value("Enter a search"));
	}

	private ResultActions search(String token, String query) throws Exception {
		return mvc.perform(post("/api/ai/search")
				.header(AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"query": "%s"}
						""".formatted(query)));
	}
}
