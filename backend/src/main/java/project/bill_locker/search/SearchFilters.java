package project.bill_locker.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import project.bill_locker.warranty.WarrantyStatus;

/**
 * How a question was understood (docs/api-contract.md §12, "SearchFilters"). Every
 * field is optional; NON_NULL leaves the unused ones out of the JSON, and the app
 * shows each one that is there as a chip ("Seller: Croma").
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchFilters(
		String text,
		String categorySlug,
		String brand,
		String seller,
		WarrantyStatus warrantyStatus,
		/** Not expired yet, and ending within this many days. */
		Integer daysUntilExpiry,
		LocalDate purchasedAfter,
		LocalDate purchasedBefore,
		BigDecimal minPrice,
		BigDecimal maxPrice,
		SortBy sortBy,
		SortDirection sortDirection,
		Integer limit) {

	public enum SortBy {
		PURCHASE_DATE, PRICE, WARRANTY_EXPIRY
	}

	public enum SortDirection {
		ASC, DESC
	}
}
