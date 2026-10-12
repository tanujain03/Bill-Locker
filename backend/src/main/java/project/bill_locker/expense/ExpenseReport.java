package project.bill_locker.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import project.bill_locker.document.BillCategory;
import project.bill_locker.document.DocumentSummary;

/**
 * The spending of one period (from … to, both included), everything the Expenses page
 * shows. Like the dashboard it only counts saved bills (their details were checked) whose
 * purchase date falls in the period.
 */
public record ExpenseReport(
		LocalDate from,
		LocalDate to,
		/** The period just before, to compare with: the same number of months (whole months) or days. */
		LocalDate previousFrom,
		LocalDate previousTo,
		BigDecimal total,
		/** Saved bills bought in the period (also those without a total). */
		long bills,
		BigDecimal previousTotal,
		/** Total ÷ the period's days up to today. */
		BigDecimal dailyAverage,
		/** Only while the period is running (it started, but ends after today): the total at this pace. */
		BigDecimal projectedTotal,
		/** The user's monthly budget, or null when none is set. */
		BigDecimal budget,
		/** Biggest first. {@code category} is null for bills nobody sorted yet. */
		List<CategorySpend> categories,
		/** The period's most expensive product lines (by unit price). */
		List<ProductSpend> topProducts,
		List<ShopSpend> topShops,
		/** Every day of the period, oldest first; empty for periods over 62 days (too many for a calendar). */
		List<DaySpend> days,
		/** The months up to the period's last one, oldest first: at least 12, more for a longer period. */
		List<MonthSpend> months,
		/** The period's most expensive bill, or null. */
		DocumentSummary biggestBill,
		/** Saved bills of the period without a category: the page asks the user to sort them. */
		long uncategorizedBills) {

	public record CategorySpend(BillCategory category, BigDecimal amount, long bills, BigDecimal previousAmount) {
	}

	public record ProductSpend(String name, BillCategory category, BigDecimal amount, String seller, UUID documentId) {
	}

	public record ShopSpend(String name, BigDecimal amount, long bills) {
	}

	public record DaySpend(LocalDate date, BigDecimal amount, long bills) {
	}

	public record MonthSpend(String month, BigDecimal amount, long bills) {
	}
}
