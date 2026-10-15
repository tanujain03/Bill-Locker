package project.bill_locker.product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import project.bill_locker.document.BillCategory;
import project.bill_locker.document.DocumentSummary;
import project.bill_locker.warranty.WarrantyStatus;

/**
 * One product's page: everything about it in one place, gathered from its bill, plus other
 * documents of the user that mention the same serial or model number.
 */
public record ProductView(
		UUID documentId,
		int position,
		// ---- the product ----
		String productName,
		String brand,
		String modelNumber,
		String serialNumber,
		BigDecimal price,
		/** The bill's category (Electronics, Appliances…). */
		BillCategory category,
		// ---- the purchase ----
		String sellerName,
		String invoiceNumber,
		LocalDate purchaseDate,
		// ---- the warranty today ----
		String warrantyProvider,
		Integer warrantyMonths,
		LocalDate warrantyStartDate,
		/** Printed, or start + months − 1 day (WarrantyRules). */
		LocalDate warrantyEndDate,
		Long daysLeft,
		WarrantyStatus status,
		String registrationUrl,
		boolean warrantyRegistered,
		// ---- owning it ----
		/** Whole days since the purchase date (0 on the day itself); null without a purchase date. */
		Long ownedDays,
		/** Price ÷ months owned (at least 1 month); null without price or purchase date. */
		BigDecimal costPerMonth,
		// ---- documents ----
		/** The bill this product is on. */
		DocumentSummary bill,
		/** Other documents of the user with a product of the same serial number, or else the same model number. */
		List<RelatedDocument> related,
		/** Facts the bill doesn't give, e.g. "Serial number" (the page links to the bill to add them). */
		List<String> missing) {

	/** {@code matchedBy} says why it is related: "serial number" or "model number". */
	public record RelatedDocument(DocumentSummary document, String matchedBy) {
	}
}
