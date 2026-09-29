package project.bill_locker.warranty;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Deterministic warranty date rules (the AI never calculates dates). */
public final class WarrantyDates {

	/** A warranty is "expiring soon" when 0..30 days are left. */
	public static final int EXPIRING_SOON_DAYS = 30;

	private WarrantyDates() {
	}

	/**
	 * Expiry = start + months − 1 day: a 24-month warranty bought on 2026-09-15 covers
	 * up to and including 2028-09-14. Month ends clamp (31 Jan + 1 month → 28 Feb → 27 Feb).
	 *
	 * @return {@code null} when the start date or period is unknown (or 0 months)
	 */
	public static LocalDate expiryDate(LocalDate startDate, Integer warrantyMonths) {
		if (startDate == null || warrantyMonths == null || warrantyMonths <= 0) {
			return null;
		}
		return startDate.plusMonths(warrantyMonths).minusDays(1);
	}

	public static WarrantyStatus status(LocalDate expiryDate, LocalDate today) {
		if (expiryDate == null) {
			return WarrantyStatus.UNKNOWN;
		}
		long daysLeft = ChronoUnit.DAYS.between(today, expiryDate);
		if (daysLeft < 0) {
			return WarrantyStatus.EXPIRED;
		}
		return daysLeft <= EXPIRING_SOON_DAYS ? WarrantyStatus.EXPIRING_SOON : WarrantyStatus.ACTIVE;
	}

	/** Days until expiry (negative once expired), or {@code null} when unknown. */
	public static Long daysRemaining(LocalDate expiryDate, LocalDate today) {
		return expiryDate == null ? null : ChronoUnit.DAYS.between(today, expiryDate);
	}
}
