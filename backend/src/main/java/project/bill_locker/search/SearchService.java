package project.bill_locker.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.product.CategoryResponse;
import project.bill_locker.product.ProductResponse;
import project.bill_locker.product.ProductService;
import project.bill_locker.search.SearchFilters.SortDirection;
import project.bill_locker.warranty.WarrantyStatus;
import project.bill_locker.warranty.WarrantySummary;

/**
 * Plain-English search over the user's own products: the question becomes filters
 * ({@link SearchInterpreter}), and the filters are applied to the user's products here.
 * Nothing from the question is ever turned into SQL.
 */
@Service
public class SearchService {

	private final ProductService productService;
	private final SearchInterpreter interpreter;

	public SearchService(ProductService productService, SearchInterpreter interpreter) {
		this.productService = productService;
		this.interpreter = interpreter;
	}

	@Transactional(readOnly = true)
	public SearchResponse search(UUID userId, String query) {
		String question = query.trim();
		List<ProductResponse> products = productService.list(userId, null, null, null);
		List<CategoryResponse> categories = productService.listCategories();
		SearchFilters filters = interpreter.interpret(question, categories, products, LocalDate.now());
		return new SearchResponse(question, filters, interpreter.explain(filters, categories), apply(products, filters));
	}

	/** Keeps the products that pass every filter, then sorts and limits them. */
	static List<ProductResponse> apply(List<ProductResponse> products, SearchFilters filters) {
		Stream<ProductResponse> results = products.stream().filter(product -> matches(product, filters));
		if (filters.sortBy() != null) {
			Comparator<ProductResponse> order = switch (filters.sortBy()) {
				case PRICE -> Comparator.comparing(ProductResponse::purchasePrice,
						Comparator.nullsFirst(Comparator.<BigDecimal>naturalOrder()));
				case PURCHASE_DATE -> Comparator.comparing(ProductResponse::purchaseDate,
						Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()));
				case WARRANTY_EXPIRY -> Comparator.comparing(
						(ProductResponse product) -> product.warranty() == null ? null : product.warranty().expiryDate(),
						Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));
			};
			results = results.sorted(filters.sortDirection() == SortDirection.DESC ? order.reversed() : order);
		}
		if (filters.limit() != null) {
			results = results.limit(filters.limit());
		}
		return results.toList();
	}

	private static boolean matches(ProductResponse product, SearchFilters filters) {
		WarrantySummary warranty = product.warranty();
		if (filters.text() != null && !SearchInterpreter.matchesText(product, filters.text())) {
			return false;
		}
		if (filters.categorySlug() != null && !filters.categorySlug().equals(product.categorySlug())) {
			return false;
		}
		if (filters.brand() != null && !filters.brand().equalsIgnoreCase(product.brand())) {
			return false;
		}
		if (filters.seller() != null && !SearchInterpreter.sellerMatches(product.seller(), filters.seller())) {
			return false;
		}
		WarrantyStatus status = warranty == null ? WarrantyStatus.UNKNOWN : warranty.status();
		if (filters.warrantyStatus() != null && filters.warrantyStatus() != status) {
			return false;
		}
		if (filters.daysUntilExpiry() != null) {
			Long daysLeft = warranty == null ? null : warranty.daysRemaining();
			if (daysLeft == null || daysLeft < 0 || daysLeft > filters.daysUntilExpiry()) {
				return false;
			}
		}
		BigDecimal price = product.purchasePrice();
		if (filters.minPrice() != null && (price == null || price.compareTo(filters.minPrice()) < 0)) {
			return false;
		}
		if (filters.maxPrice() != null && (price == null || price.compareTo(filters.maxPrice()) > 0)) {
			return false;
		}
		LocalDate bought = product.purchaseDate();
		if (filters.purchasedAfter() != null && (bought == null || bought.isBefore(filters.purchasedAfter()))) {
			return false;
		}
		return filters.purchasedBefore() == null || (bought != null && !bought.isAfter(filters.purchasedBefore()));
	}
}
