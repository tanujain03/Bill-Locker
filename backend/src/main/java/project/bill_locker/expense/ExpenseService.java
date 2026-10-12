package project.bill_locker.expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.BillCategory;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentItem;
import project.bill_locker.document.DocumentRepository;
import project.bill_locker.document.DocumentSummary;
import project.bill_locker.user.User;
import project.bill_locker.user.UserRepository;

/**
 * Works out where the money went in one period (this month, last 6 months, a custom
 * range…), from the user's saved bills. All the adding up happens here (not in the
 * browser), so it follows one set of rules and is tested.
 */
@Service
public class ExpenseService {

	private static final int MONTHS = 12;
	private static final int TOP_PRODUCTS = 6;
	private static final int TOP_SHOPS = 5;
	/** Longer periods get no day-by-day calendar. */
	private static final int MAX_CALENDAR_DAYS = 62;
	/** About 3 years: enough for any question, small enough to stay quick. */
	private static final int MAX_DAYS = 1100;

	private final DocumentRepository documents;
	private final UserRepository users;
	private final Clock clock;

	public ExpenseService(DocumentRepository documents, UserRepository users, Clock clock) {
		this.documents = documents;
		this.users = users;
		this.clock = clock;
	}

	/** {@code fromText}/{@code toText} are "YYYY-MM-DD", both included; without them, this month. */
	@Transactional(readOnly = true)
	public ExpenseReport report(UUID userId, String fromText, String toText) {
		YearMonth thisMonth = YearMonth.now(clock);
		LocalDate from = parseDate(fromText, thisMonth.atDay(1));
		LocalDate to = parseDate(toText, thisMonth.atEndOfMonth());
		if (from.isAfter(to)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "The start date must be on or before the end date.");
		}
		if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "RANGE_TOO_LONG", "Pick a period of 3 years or less.");
		}
		LocalDate[] previousPeriod = previousPeriod(from, to);
		User user = users.findById(userId).orElseThrow(); // the token's user always exists

		List<Document> dated = documents.findSavedWithItems(userId).stream()
				.filter(d -> d.getPurchaseDate() != null).toList();
		List<Document> bills = between(dated, from, to);
		List<Document> previous = between(dated, previousPeriod[0], previousPeriod[1]);
		BigDecimal total = sum(bills);

		return new ExpenseReport(
				from,
				to,
				previousPeriod[0],
				previousPeriod[1],
				total,
				bills.size(),
				sum(previous),
				dailyAverage(total, from, to),
				projectedTotal(total, from, to),
				user.getMonthlyBudget(),
				categories(bills, previous),
				topProducts(bills),
				topShops(bills),
				ChronoUnit.DAYS.between(from, to) < MAX_CALENDAR_DAYS ? days(bills, from, to) : List.of(),
				months(dated, from, to),
				bills.stream().filter(d -> d.getTotalAmount() != null)
						.max(Comparator.comparing(Document::getTotalAmount)).map(DocumentSummary::of).orElse(null),
				bills.stream().filter(d -> BillCategory.parse(d.getCategory()) == null).count());
	}

	@Transactional
	public void setBudget(UUID userId, BigDecimal amount) {
		users.findById(userId).orElseThrow().setMonthlyBudget(amount);
	}

	/** "2026-10-07" → that date; no value → the fallback. A future date is fine: it is just empty. */
	private static LocalDate parseDate(String text, LocalDate fallback) {
		if (text == null || text.isBlank()) {
			return fallback;
		}
		try {
			return LocalDate.parse(text.strip());
		} catch (DateTimeParseException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "Dates must look like 2026-10-07.");
		}
	}

	/**
	 * The period to compare with, just before this one. Whole months compare with the same
	 * number of whole months (October with September, not 31 Aug to 30 Sep); anything else
	 * with the same number of days.
	 */
	private static LocalDate[] previousPeriod(LocalDate from, LocalDate to) {
		if (from.getDayOfMonth() == 1 && to.equals(YearMonth.from(to).atEndOfMonth())) {
			long months = ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
			return new LocalDate[] {from.minusMonths(months), from.minusDays(1)};
		}
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		return new LocalDate[] {from.minusDays(days), from.minusDays(1)};
	}

	private static List<Document> between(List<Document> bills, LocalDate from, LocalDate to) {
		return bills.stream()
				.filter(d -> !d.getPurchaseDate().isBefore(from) && !d.getPurchaseDate().isAfter(to))
				.toList();
	}

	/** Total ÷ the period's days up to today (today included); a period that hasn't started → 0. */
	private BigDecimal dailyAverage(BigDecimal total, LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.now(clock);
		LocalDate last = to.isBefore(today) ? to : today;
		long days = last.isBefore(from) ? 0 : ChronoUnit.DAYS.between(from, last) + 1;
		return days == 0 ? money(BigDecimal.ZERO) : total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
	}

	/** Only while the period runs (started, ends after today): the daily average so far × all its days. */
	private BigDecimal projectedTotal(BigDecimal total, LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.now(clock);
		if (today.isBefore(from) || !to.isAfter(today)) {
			return null;
		}
		long all = ChronoUnit.DAYS.between(from, to) + 1;
		long soFar = ChronoUnit.DAYS.between(from, today) + 1;
		return total.multiply(BigDecimal.valueOf(all)).divide(BigDecimal.valueOf(soFar), 2, RoundingMode.HALF_UP);
	}

	/** Money per category in the period (biggest first), with the previous period's amount to compare. */
	private static List<ExpenseReport.CategorySpend> categories(List<Document> bills, List<Document> previous) {
		Map<BillCategory, List<Document>> now = byCategory(bills);
		Map<BillCategory, List<Document>> before = byCategory(previous);
		return now.entrySet().stream()
				.map(e -> new ExpenseReport.CategorySpend(e.getKey(), sum(e.getValue()), e.getValue().size(),
						sum(before.getOrDefault(e.getKey(), List.of()))))
				.sorted(Comparator.comparing(ExpenseReport.CategorySpend::amount).reversed())
				.toList();
	}

	/** Bills per category; the key null holds the bills nobody sorted yet. */
	private static Map<BillCategory, List<Document>> byCategory(List<Document> bills) {
		Map<BillCategory, List<Document>> map = new LinkedHashMap<>();
		for (Document d : bills) {
			map.computeIfAbsent(BillCategory.parse(d.getCategory()), c -> new ArrayList<>()).add(d);
		}
		return map;
	}

	/** The period's most expensive product lines. Bills have no quantity, so one line = its unit price. */
	private static List<ExpenseReport.ProductSpend> topProducts(List<Document> bills) {
		List<ExpenseReport.ProductSpend> products = new ArrayList<>();
		for (Document d : bills) {
			for (DocumentItem item : d.getItems()) {
				if (item.getProductName() != null && item.getUnitPrice() != null) {
					products.add(new ExpenseReport.ProductSpend(item.getProductName(), BillCategory.parse(d.getCategory()),
							money(item.getUnitPrice()), d.getSellerName(), d.getId()));
				}
			}
		}
		products.sort(Comparator.comparing(ExpenseReport.ProductSpend::amount).reversed());
		return products.stream().limit(TOP_PRODUCTS).toList();
	}

	/** Shops by money spent in the period; "Croma" and "croma " are the same shop. */
	private static List<ExpenseReport.ShopSpend> topShops(List<Document> bills) {
		Map<String, List<Document>> byShop = new LinkedHashMap<>();
		for (Document d : bills) {
			if (d.getSellerName() != null && !d.getSellerName().isBlank() && d.getTotalAmount() != null) {
				byShop.computeIfAbsent(d.getSellerName().strip().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(d);
			}
		}
		return byShop.values().stream()
				.map(shop -> new ExpenseReport.ShopSpend(shop.getFirst().getSellerName().strip(), sum(shop), shop.size()))
				.sorted(Comparator.comparing(ExpenseReport.ShopSpend::amount).reversed())
				.limit(TOP_SHOPS)
				.toList();
	}

	/** One entry per day of the period (zeros included), for the calendar. */
	private static List<ExpenseReport.DaySpend> days(List<Document> bills, LocalDate from, LocalDate to) {
		List<ExpenseReport.DaySpend> days = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			LocalDate day = date;
			List<Document> that = bills.stream().filter(d -> d.getPurchaseDate().equals(day)).toList();
			days.add(new ExpenseReport.DaySpend(day, sum(that), that.size()));
		}
		return days;
	}

	/**
	 * Whole months ending with the period's last month, oldest first, zeros included: the
	 * period's months, but at least 12, so a single month still shows the year around it.
	 */
	private static List<ExpenseReport.MonthSpend> months(List<Document> dated, LocalDate from, LocalDate to) {
		YearMonth last = YearMonth.from(to);
		long count = Math.max(MONTHS, ChronoUnit.MONTHS.between(YearMonth.from(from), last) + 1);
		Map<YearMonth, List<Document>> byMonth = new LinkedHashMap<>();
		for (Document d : dated) {
			byMonth.computeIfAbsent(YearMonth.from(d.getPurchaseDate()), m -> new ArrayList<>()).add(d);
		}
		List<ExpenseReport.MonthSpend> months = new ArrayList<>();
		for (long i = count - 1; i >= 0; i--) {
			YearMonth m = last.minusMonths(i);
			List<Document> bills = byMonth.getOrDefault(m, List.of());
			months.add(new ExpenseReport.MonthSpend(m.toString(), sum(bills), bills.size()));
		}
		return months;
	}

	/** Sum of the bills' totals (bills without a total add nothing), always with 2 decimals. */
	private static BigDecimal sum(List<Document> bills) {
		return money(bills.stream().map(Document::getTotalAmount).filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add));
	}

	private static BigDecimal money(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.HALF_UP);
	}
}
