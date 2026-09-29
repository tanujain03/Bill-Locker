package project.bill_locker.document;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What the AI read from a document, stored as JSON on {@link Document#getExtraction()}.
 * A suggestion only: products and warranties change solely through the user's
 * "Confirm &amp; Save". Values the AI could not find are {@code null} — never guessed.
 *
 * @param purchaseDate ISO date {@code YYYY-MM-DD}, as in the API contract
 * @param confidence   0..1 per field name (e.g. {@code "purchaseDate" -> 0.97})
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
