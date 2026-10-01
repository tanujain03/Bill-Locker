package project.bill_locker.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import project.bill_locker.warranty.WarrantySummary;

/**
 * A product as the API returns it (docs/api-contract.md §5, "Product"). Service
 * records come in a later step, so nextServiceDate is always null for now.
 */
public record ProductResponse(
		UUID id,
		UUID categoryId,
		String categoryName,
		String categorySlug,
		String name,
		String brand,
		String model,
		String serialNumber,
		LocalDate purchaseDate,
		BigDecimal purchasePrice,
		String currency,
		String seller,
		String invoiceNumber,
		WarrantySummary warranty,
		LocalDate nextServiceDate,
		long documentCount,
		Instant createdAt,
		Instant updatedAt) {

	static ProductResponse from(Product product, long documentCount, LocalDate today) {
		Category category = product.getCategory();
		return new ProductResponse(product.getId(),
				category == null ? null : category.getId(),
				category == null ? null : category.getName(),
				category == null ? null : category.getSlug(),
				product.getName(), product.getBrand(), product.getModel(), product.getSerialNumber(),
				product.getPurchaseDate(), product.getPurchasePrice(), product.getCurrency(), product.getSeller(),
				product.getInvoiceNumber(),
				product.getWarranty() == null ? null : WarrantySummary.of(product.getWarranty(), today),
				null, documentCount, product.getCreatedAt(), product.getUpdatedAt());
	}
}
