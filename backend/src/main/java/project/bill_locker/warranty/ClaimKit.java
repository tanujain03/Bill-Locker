package project.bill_locker.warranty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything needed to claim one product's warranty, gathered from its bill: the facts a
 * service centre asks for, how the warranty stands today, where to turn, and which facts
 * are still missing. The claim email itself is written in the browser from these facts.
 */
public record ClaimKit(
		UUID documentId,
		int position,
		/** The stored bill's file name, for "Download invoice". */
		String fileName,
		// ---- the product ----
		String productName,
		String brand,
		String modelNumber,
		String serialNumber,
		BigDecimal unitPrice,
		// ---- the purchase ----
		String sellerName,
		String sellerContact,
		String sellerAddress,
		String invoiceNumber,
		LocalDate purchaseDate,
		String buyerName,
		// ---- the warranty today ----
		String warrantyProvider,
		LocalDate warrantyStartDate,
		/** Printed, or worked out from start + months (WarrantyRules.effectiveEnd). */
		LocalDate warrantyEndDate,
		Long daysLeft,
		WarrantyStatus status,
		// ---- where to turn ----
		/** The brand's registration page from the bill or a search (task 5), or null. */
		String registrationUrl,
		/** A web search for the brand's customer care / warranty claim page. */
		String supportSearchUrl,
		/** A Google Maps search for the brand's service centres near the user. */
		String serviceCentreUrl,
		/** What a claim usually needs but this bill doesn't give, e.g. "Serial number". */
		List<String> missing) {
}
