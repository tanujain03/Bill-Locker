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
		/** What it was spent on (any document type). */
		BillCategory category,
		/** The last day the purchase can be returned (task 14); null when unknown or not returnable. */
		LocalDate returnByDate) {

	public DocumentDetails {
		items = items == null ? List.of() : List.copyOf(items);
	}

	/** Without a category (invoices, warranty cards, and older code and tests). */
	public DocumentDetails(DocumentType documentType, String documentNumber, String sellerName, String sellerAddress,
			String sellerContact, String buyerName, String buyerAddress, String buyerEmail, LocalDate purchaseDate,
			BigDecimal taxAmount, BigDecimal totalAmount, List<DocumentItemView> items) {
		this(documentType, documentNumber, sellerName, sellerAddress, sellerContact, buyerName, buyerAddress, buyerEmail,
				purchaseDate, taxAmount, totalAmount, items, null, null);
	}

	/** Without a return date (most callers and tests from before task 14). */
	public DocumentDetails(DocumentType documentType, String documentNumber, String sellerName, String sellerAddress,
			String sellerContact, String buyerName, String buyerAddress, String buyerEmail, LocalDate purchaseDate,
			BigDecimal taxAmount, BigDecimal totalAmount, List<DocumentItemView> items, BillCategory category) {
		this(documentType, documentNumber, sellerName, sellerAddress, sellerContact, buyerName, buyerAddress, buyerEmail,
				purchaseDate, taxAmount, totalAmount, items, category, null);
	}

	/** The same details with other products. */
	DocumentDetails withItems(List<DocumentItemView> newItems) {
		return new DocumentDetails(documentType, documentNumber, sellerName, sellerAddress, sellerContact, buyerName,
				buyerAddress, buyerEmail, purchaseDate, taxAmount, totalAmount, newItems, category, returnByDate);
	}
}
