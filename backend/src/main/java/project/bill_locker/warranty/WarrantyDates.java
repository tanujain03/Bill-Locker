package project.bill_locker.warranty;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** The warranty date rules in one place: plain Java, so dates are always calculated, never guessed. */
public final class WarrantyDates {

	/** "Expiring soon" means 0 to 30 days left. */
	public static final int EXPIRING_SOON_DAYS = 30;

	private WarrantyDates() {
	}

	/**
	 * Expiry = start + months − 1 day: a 24-month warranty bought on 2026-09-15 covers up to
	 * and including 2028-09-14. Null when the start date or the period is unknown (or 0 months).
	 */
	public static LocalDate expiryDate(LocalDate startDate, Integer months) {
		if (startDate == null || months == null || months <= 0) {
			return null;
		}
		return startDate.plusMonths(months).minusDays(1);
	}

	/** Whole days from today until the expiry date (negative once expired), or null when unknown. */
	public static Long daysRemaining(LocalDate expiryDate, LocalDate today) {
		return expiryDate == null ? null : ChronoUnit.DAYS.between(today, expiryDate);
	}

	public static WarrantyStatus status(LocalDate expiryDate, LocalDate today) {
		Long daysLeft = daysRemaining(expiryDate, today);
		if (daysLeft == null) {
			return WarrantyStatus.UNKNOWN;
		}
		if (daysLeft < 0) {
			return WarrantyStatus.EXPIRED;
		}
		return daysLeft <= EXPIRING_SOON_DAYS ? WarrantyStatus.EXPIRING_SOON : WarrantyStatus.ACTIVE;
	}
}
