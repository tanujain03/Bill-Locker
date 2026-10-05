package project.bill_locker.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import project.bill_locker.product.CategoryResponse;
import project.bill_locker.product.ProductResponse;
import project.bill_locker.search.SearchFilters.SortBy;
import project.bill_locker.search.SearchFilters.SortDirection;
import project.bill_locker.warranty.WarrantyStatus;

/** How plain-English questions are turned into filters. Plain Java: no database needed. */
class SearchInterpreterTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
	private static final List<CategoryResponse> CATEGORIES = List.of(
			new CategoryResponse(UUID.randomUUID(), "Home Appliances", "home-appliances"),
			new CategoryResponse(UUID.randomUUID(), "Computers & Accessories", "computers"));
	private static final List<ProductResponse> PRODUCTS = List.of(
			product("Inspiron 15 Laptop", "Dell", "Croma Retail, Bengaluru"),
			product("Double Door Refrigerator", "LG", "Reliance Digital"));

	private final SearchInterpreter interpreter = new SearchInterpreter();

	@Test
	void understandsWhenWarrantiesEnd() {
		SearchFilters within = interpret("Warranties expiring within 90 days");
		assertThat(within.daysUntilExpiry()).isEqualTo(90);
		assertThat(within.sortBy()).isEqualTo(SortBy.WARRANTY_EXPIRY);
		assertThat(within.sortDirection()).isEqualTo(SortDirection.ASC);
		assertThat(interpret("warranty ending in 2 months").daysUntilExpiry()).isEqualTo(60);
		assertThat(interpret("Expired warranties").warrantyStatus()).isEqualTo(WarrantyStatus.EXPIRED);
		assertThat(interpret("what is still under warranty?").warrantyStatus()).isEqualTo(WarrantyStatus.ACTIVE);
	}

	@Test
	void recognisesTheUsersSellersBrandsAndKindsOfProduct() {
		assertThat(interpret("What did I buy from Croma?").seller()).isEqualTo("Croma");
		assertThat(interpret("What did I buy from Amazon?").seller()).as("no product from Amazon").isNull();
		SearchFilters fridge = interpret("show my LG fridge");
		assertThat(fridge.brand()).isEqualTo("LG");
		assertThat(fridge.text()).isEqualTo("fridge");
	}

	@Test
	void understandsPricesDatesAndOrder() {
		SearchFilters appliances = interpret("appliances over 50k bought in 2025");
		assertThat(appliances.categorySlug()).isEqualTo("home-appliances");
		assertThat(appliances.minPrice()).isEqualByComparingTo("50000");
		assertThat(appliances.purchasedAfter()).isEqualTo(LocalDate.of(2025, 1, 1));
		assertThat(appliances.purchasedBefore()).isEqualTo(LocalDate.of(2025, 12, 31));
		assertThat(interpret("things under 1 lakh").maxPrice()).isEqualByComparingTo("100000");

		SearchFilters mostExpensive = interpret("Most expensive purchase");
		assertThat(mostExpensive.sortBy()).isEqualTo(SortBy.PRICE);
		assertThat(mostExpensive.sortDirection()).isEqualTo(SortDirection.DESC);
		assertThat(mostExpensive.limit()).isEqualTo(1);
	}

	@Test
	void withoutAKnownPhraseItSearchesForTheWords() {
		assertThat(interpret("show me my bosch").text()).isEqualTo("bosch");
		assertThat(interpret("show me all my products")).as("nothing to filter by")
				.isEqualTo(new SearchFilters(null, null, null, null, null, null, null, null, null, null, null, null, null));
	}

	@Test
	void explainsHowTheQuestionWasUnderstood() {
		assertThat(interpreter.explain(interpret("Warranties expiring within 90 days"), CATEGORIES))
				.isEqualTo("Showing products whose warranty expires within the next 90 days, soonest expiry first.");
		assertThat(interpreter.explain(interpret("appliances over 50k"), CATEGORIES))
				.isEqualTo("Showing products in Home Appliances, costing at least ₹50,000.");
		assertThat(interpreter.explain(interpret("Most expensive purchase"), CATEGORIES))
				.isEqualTo("Showing your most expensive purchase.");
	}

	@Test
	void aFridgeIsFoundAsARefrigerator() {
		assertThat(SearchInterpreter.matchesText(PRODUCTS.get(1), "fridge")).isTrue();
		assertThat(SearchInterpreter.matchesText(PRODUCTS.getFirst(), "fridge")).isFalse();
	}

	private SearchFilters interpret(String question) {
		return interpreter.interpret(question, CATEGORIES, PRODUCTS, TODAY);
	}

	private static ProductResponse product(String name, String brand, String seller) {
		return new ProductResponse(UUID.randomUUID(), null, null, null, name, brand, null, null, null,
				BigDecimal.TEN, "INR", seller, null, null, null, 0, Instant.now(), Instant.now());
	}
}
