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
		List<DocumentItemView> items,
		/** Only for bills and receipts (RECEIPT): what it was for. */
		BillCategory category) {

	public DocumentDetails {
		items = items == null ? List.of() : List.copyOf(items);
	}

	/** Without a category (invoices, warranty cards, and older code and tests). */
	public DocumentDetails(DocumentType documentType, String documentNumber, String sellerName, String sellerAddress,
			String sellerContact, String buyerName, String buyerAddress, String buyerEmail, LocalDate purchaseDate,
			BigDecimal taxAmount, BigDecimal totalAmount, List<DocumentItemView> items) {
		this(documentType, documentNumber, sellerName, sellerAddress, sellerContact, buyerName, buyerAddress, buyerEmail,
				purchaseDate, taxAmount, totalAmount, items, null);
	}

	/** The same details with other products. */
	DocumentDetails withItems(List<DocumentItemView> newItems) {
		return new DocumentDetails(documentType, documentNumber, sellerName, sellerAddress, sellerContact, buyerName,
				buyerAddress, buyerEmail, purchaseDate, taxAmount, totalAmount, newItems, category);
	}
}
