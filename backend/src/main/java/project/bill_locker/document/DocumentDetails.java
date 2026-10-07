package project.bill_locker.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The details read from a bill: what the AI returns and what the user saves.
 * The fields follow invoice_warranty_fields.csv; the product fields are per item.
 */
public record DocumentDetails(
		DocumentType documentType,
		String documentNumber,
		String sellerName,
		String sellerAddress,
		String sellerContact,
		String buyerName,
		String buyerAddress,
		String buyerEmail,
		LocalDate purchaseDate,
		BigDecimal taxAmount,
		BigDecimal totalAmount,
		List<DocumentItemView> items) {

	public DocumentDetails {
		items = items == null ? List.of() : List.copyOf(items);
	}
}
