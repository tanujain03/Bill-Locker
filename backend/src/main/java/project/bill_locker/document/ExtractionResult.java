package project.bill_locker.document;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The details found in a document's text and codes (docs/api-contract.md,
 * "ExtractionResult"). They are suggestions for the user to check, never saved product
 * data. A value that was not found is null ("Not found" in the app); {@code confidence}
 * holds 0..1 for each value that was found. {@code codes} lists the barcodes and QR
 * codes on the document (null for documents read before step 7).
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
		Map<String, Double> confidence,
		List<ScannedCode> codes) {
}
