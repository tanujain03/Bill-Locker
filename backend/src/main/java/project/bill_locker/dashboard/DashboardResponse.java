package project.bill_locker.dashboard;

import java.math.BigDecimal;
import java.util.List;
import project.bill_locker.document.DocumentSummary;
import project.bill_locker.warranty.WarrantyView;

/**
 * Everything the dashboard shows, in one answer. Money, products and warranties
 * come from saved bills only; {@code attention} counts what still needs the user.
 */
public record DashboardResponse(
		long savedBills,
		long savedBillsThisMonth,
		long products,
		long productsWithWarranty,
		BigDecimal totalSpent,
		BigDecimal spentThisMonth,
		Attention attention,
		WarrantyCounts warranties,
		/** The 5 nearest "expiring soon" warranties, soonest first. */
		List<WarrantyView> expiringSoon,
		/** Always 12 months, oldest first, the current month last; months without bills are 0. */
		List<MonthSpend> spendingByMonth,
		/** Saved bills missing a purchase date or a total: they add no money to the chart. */
		long billsWithoutDateOrTotal,
		List<ShopSpend> topShops,
		/** The 5 newest bills of any status. */
		List<DocumentSummary> recentBills) {

	public record Attention(long toReview, long readFailed, long reading) {
	}

	public record WarrantyCounts(long active, long expiringSoon, long expired, long noInfo) {
	}

	/** {@code month} is "YYYY-MM"; {@code bills} = saved bills bought that month (with or without a total). */
	public record MonthSpend(String month, BigDecimal amount, long bills) {
	}

	public record ShopSpend(String name, BigDecimal amount, long bills) {
	}
}
