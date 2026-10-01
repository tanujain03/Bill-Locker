package project.bill_locker.warranty;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;
import java.util.UUID;
import project.bill_locker.document.Document;
import project.bill_locker.product.Category;
import project.bill_locker.product.Product;

/**
 * One warranty in {@code GET /api/warranties} and on the dashboard (docs/api-contract.md
 * §6, "Warranty"): the summary's fields at the top level, plus the product it covers.
 */
public record WarrantyResponse(
		@JsonUnwrapped WarrantySummary summary,
		UUID productId,
		String productName,
		String productBrand,
		String categoryName,
		String categorySlug,
		UUID sourceDocumentId) {

	public static WarrantyResponse of(Warranty warranty, LocalDate today) {
		Product product = warranty.getProduct();
		Category category = product.getCategory();
		Document source = warranty.getSourceDocument();
		return new WarrantyResponse(WarrantySummary.of(warranty, today), product.getId(), product.getName(),
				product.getBrand(), category == null ? null : category.getName(),
				category == null ? null : category.getSlug(), source == null ? null : source.getId());
	}
}
