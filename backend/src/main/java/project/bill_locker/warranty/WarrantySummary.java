package project.bill_locker.warranty;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The warranty part of a product in the API (docs/api-contract.md §5–6, "WarrantySummary").
 * Status and days remaining are worked out for {@code today}.
 */
public record WarrantySummary(
		UUID id,
		Integer warrantyMonths,
		LocalDate startDate,
		LocalDate expiryDate,
		WarrantyStatus status,
		Long daysRemaining) {

	public static WarrantySummary of(Warranty warranty, LocalDate today) {
		return new WarrantySummary(warranty.getId(), warranty.getWarrantyMonths(), warranty.getStartDate(),
				warranty.getExpiryDate(), warranty.statusOn(today), warranty.daysRemainingOn(today));
	}
}
