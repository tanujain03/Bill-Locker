package project.bill_locker.warranty;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The warranty date rules: plain Java, so they can be tested without a database. */
class WarrantyDatesTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

	@Test
	void aWarrantyEndsTheDayBeforeTheSameDateMonthsLater() {
		assertThat(WarrantyDates.expiryDate(LocalDate.of(2026, 9, 15), 24)).isEqualTo(LocalDate.of(2028, 9, 14));
		assertThat(WarrantyDates.expiryDate(LocalDate.of(2026, 1, 31), 1))
				.as("31 Jan + 1 month is 28 Feb (no 31st), minus a day")
				.isEqualTo(LocalDate.of(2026, 2, 27));
	}

	@Test
	void withoutAPurchaseDateOrAPeriodTheWarrantyIsUnknown() {
		assertThat(WarrantyDates.expiryDate(null, 12)).isNull();
		assertThat(WarrantyDates.expiryDate(TODAY, null)).isNull();
		assertThat(WarrantyDates.expiryDate(TODAY, 0)).isNull();
		assertThat(WarrantyDates.status(null, TODAY)).isEqualTo(WarrantyStatus.UNKNOWN);
		assertThat(WarrantyDates.daysRemaining(null, TODAY)).isNull();
	}

	@Test
	void theStatusComesFromTheDaysLeft() {
		assertThat(WarrantyDates.status(TODAY.plusDays(31), TODAY)).isEqualTo(WarrantyStatus.ACTIVE);
		assertThat(WarrantyDates.status(TODAY.plusDays(30), TODAY)).isEqualTo(WarrantyStatus.EXPIRING_SOON);
		assertThat(WarrantyDates.status(TODAY, TODAY)).as("the last day is still covered").isEqualTo(WarrantyStatus.EXPIRING_SOON);
		assertThat(WarrantyDates.status(TODAY.minusDays(1), TODAY)).isEqualTo(WarrantyStatus.EXPIRED);
		assertThat(WarrantyDates.daysRemaining(TODAY.plusDays(30), TODAY)).isEqualTo(30L);
		assertThat(WarrantyDates.daysRemaining(TODAY.minusDays(3), TODAY)).isEqualTo(-3L);
	}
}
