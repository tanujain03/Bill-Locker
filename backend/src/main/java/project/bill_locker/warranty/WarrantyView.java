package project.bill_locker.warranty;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One product's warranty, as the dashboard and the warranties page show it.
 * {@code endDate} is the effective end (printed, or worked out); {@code daysLeft}
 * is negative once expired and null when the end is unknown.
 */
public record WarrantyView(
		UUID documentId,
		String productName,
		String modelNumber,
		String serialNumber,
		String sellerName,
		String warrantyProvider,
		LocalDate purchaseDate,
		LocalDate startDate,
		LocalDate endDate,
		Long daysLeft,
		WarrantyStatus status) {
}
