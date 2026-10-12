package project.bill_locker.warranty;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The warranty rules on their own: no Spring, no database. */
class WarrantyRulesTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	@Test
	void endDateWins() {
		assertThat(WarrantyRules.effectiveEnd(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), 12))
				.isEqualTo(LocalDate.of(2027, 1, 1));
	}

	@Test
	void startPlusMonthsMinusOneDay() {
		assertThat(WarrantyRules.effectiveEnd(null, LocalDate.of(2026, 1, 10), 12)).isEqualTo(LocalDate.of(2027, 1, 9));
	}

	@Test
	void endOfMonthClamps() {
		// 31 Jan + 1 month = 28 Feb (Java clamps), minus one day.
		assertThat(WarrantyRules.effectiveEnd(null, LocalDate.of(2026, 1, 31), 1)).isEqualTo(LocalDate.of(2026, 2, 27));
	}

	@Test
	void zeroMonthsIsNoInfo() {
		assertThat(WarrantyRules.effectiveEnd(null, LocalDate.of(2026, 1, 10), 0)).isNull();
		assertThat(WarrantyRules.status(null, TODAY)).isEqualTo(WarrantyStatus.NO_INFO);
	}

	@Test
	void startWithoutMonthsIsNoInfo() {
		assertThat(WarrantyRules.effectiveEnd(null, LocalDate.of(2026, 1, 10), null)).isNull();
	}

	@Test
	void boundaries() {
		assertThat(WarrantyRules.status(TODAY.minusDays(1), TODAY)).isEqualTo(WarrantyStatus.EXPIRED);
		assertThat(WarrantyRules.status(TODAY, TODAY)).isEqualTo(WarrantyStatus.EXPIRING_SOON);
		assertThat(WarrantyRules.status(TODAY.plusDays(30), TODAY)).isEqualTo(WarrantyStatus.EXPIRING_SOON);
		assertThat(WarrantyRules.status(TODAY.plusDays(31), TODAY)).isEqualTo(WarrantyStatus.ACTIVE);
	}

	@Test
	void daysLeft() {
		assertThat(WarrantyRules.daysLeft(TODAY.plusDays(12), TODAY)).isEqualTo(12L);
		assertThat(WarrantyRules.daysLeft(TODAY.minusDays(3), TODAY)).isEqualTo(-3L);
		assertThat(WarrantyRules.daysLeft(null, TODAY)).isNull();
	}
}
