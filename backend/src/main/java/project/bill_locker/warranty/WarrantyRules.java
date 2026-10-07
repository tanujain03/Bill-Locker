package project.bill_locker.warranty;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The warranty rules in one place, so the dashboard and the warranties page can
 * never disagree. Plain static methods: "today" is passed in, which keeps them
 * easy to test.
 */
public final class WarrantyRules {

	/** "Expiring soon" means this many days left, or fewer. */
	public static final int SOON_DAYS = 30;

	private WarrantyRules() {
	}

	/**
	 * The end date printed on the bill, or else start + months − 1 day (a 12-month
	 * warranty from 10 Jan 2026 covers up to 9 Jan 2027). Null when neither is known;
	 * 0 months is treated as unknown, not as "ended the day before it started".
	 */
	public static LocalDate effectiveEnd(LocalDate end, LocalDate start, Integer months) {
		if (end != null) {
			return end;
		}
		if (start == null || months == null || months <= 0) {
			return null;
		}
		return start.plusMonths(months).minusDays(1);
	}

	public static WarrantyStatus status(LocalDate effectiveEnd, LocalDate today) {
		if (effectiveEnd == null) {
			return WarrantyStatus.NO_INFO;
		}
		if (effectiveEnd.isBefore(today)) {
			return WarrantyStatus.EXPIRED;
		}
		return effectiveEnd.isAfter(today.plusDays(SOON_DAYS)) ? WarrantyStatus.ACTIVE : WarrantyStatus.EXPIRING_SOON;
	}

	/** Days until the end (negative once it has passed); null when the end is unknown. */
	public static Long daysLeft(LocalDate effectiveEnd, LocalDate today) {
		return effectiveEnd == null ? null : ChronoUnit.DAYS.between(today, effectiveEnd);
	}
}
