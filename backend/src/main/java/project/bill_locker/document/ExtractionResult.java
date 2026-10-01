package project.bill_locker.document;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The details found in a document's text (docs/api-contract.md, "ExtractionResult").
 * They are suggestions for the user to check, never saved product data. A value that
 * was not found is null ("Not found" in the app); {@code confidence} holds 0..1 for
 * each value that was found.
 */
public record ExtractionResult(
		DocumentType documentType,
		String productName,
		String brand,
		String model,
		String serialNumber,
		String purchaseDate,
		BigDecimal purchasePrice,
		String currency,
		String seller,
		String invoiceNumber,
		Integer warrantyMonths,
		String suggestedCategorySlug,
		Map<String, Double> confidence) {
}
