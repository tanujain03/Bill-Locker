package project.bill_locker.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentRepository;
import project.bill_locker.document.DocumentStatus;
import project.bill_locker.document.DocumentSummary;
import project.bill_locker.warranty.WarrantyService;
import project.bill_locker.warranty.WarrantyStatus;
import project.bill_locker.warranty.WarrantyView;

/**
 * Works out the dashboard from the user's bills. Everything is computed here, not
 * in the browser, so the numbers follow one set of rules and can be tested.
 */
@Service
public class DashboardService {

	private static final int MONTHS = 12;
	private static final int TOP = 5;

	private final DocumentRepository documents;
	private final WarrantyService warranties;
	private final Clock clock;

	public DashboardService(DocumentRepository documents, WarrantyService warranties, Clock clock) {
		this.documents = documents;
		this.warranties = warranties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public DashboardResponse summary(UUID userId) {
		YearMonth thisMonth = YearMonth.now(clock);
		List<Document> saved = documents.findSavedWithItems(userId);
		List<WarrantyView> views = warranties.views(userId);

		List<Document> savedThisMonth = saved.stream()
				.filter(d -> d.getPurchaseDate() != null && YearMonth.from(d.getPurchaseDate()).equals(thisMonth))
				.toList();

		return new DashboardResponse(
				saved.size(),
				savedThisMonth.size(),
				views.size(),
				views.stream().filter(v -> v.status() != WarrantyStatus.NO_INFO).count(),
				sum(saved),
				sum(savedThisMonth),
				new DashboardResponse.Attention(
						documents.countByUserIdAndStatus(userId, DocumentStatus.EXTRACTED),
						documents.countByUserIdAndStatusAndReadErrorIsNotNullAndReadQueuedAtIsNull(userId,
								DocumentStatus.UPLOADED),
						documents.countByUserIdAndReadQueuedAtIsNotNull(userId)),
				new DashboardResponse.WarrantyCounts(count(views, WarrantyStatus.ACTIVE),
						count(views, WarrantyStatus.EXPIRING_SOON), count(views, WarrantyStatus.EXPIRED),
						count(views, WarrantyStatus.NO_INFO)),
				// The views come sorted with "expiring soon" first, soonest first.
				views.stream().filter(v -> v.status() == WarrantyStatus.EXPIRING_SOON).limit(TOP).toList(),
				spendingByMonth(saved, thisMonth),
				saved.stream().filter(d -> d.getPurchaseDate() == null || d.getTotalAmount() == null).count(),
				topShops(saved),
				documents.findTop5ByUserIdOrderByCreatedAtDesc(userId).stream().map(DocumentSummary::of).toList());
	}

	/** One entry per month for the last 12 months (zeros included), oldest first. */
	private static List<DashboardResponse.MonthSpend> spendingByMonth(List<Document> saved, YearMonth thisMonth) {
		Map<YearMonth, List<Document>> byMonth = new LinkedHashMap<>();
		for (int i = MONTHS - 1; i >= 0; i--) {
			byMonth.put(thisMonth.minusMonths(i), new ArrayList<>());
		}
		for (Document d : saved) {
			// A bill without a total still counts as a bill of its month (it adds no money), so
			// the bar's "4 bills" matches the list it opens: saved bills bought that month.
			if (d.getPurchaseDate() != null) {
				List<Document> bucket = byMonth.get(YearMonth.from(d.getPurchaseDate()));
				if (bucket != null) { // outside the 12 months (older, or a future date): not on the chart
					bucket.add(d);
				}
			}
		}
		return byMonth.entrySet().stream()
				.map(e -> new DashboardResponse.MonthSpend(e.getKey().toString(), sum(e.getValue()), e.getValue().size()))
				.toList();
	}

	/**
	 * Shops by money spent. "Croma" and "croma " are the same shop; it is shown with
	 * the spelling of its most recent bill.
	 */
	private static List<DashboardResponse.ShopSpend> topShops(List<Document> saved) {
		Map<String, List<Document>> byShop = new LinkedHashMap<>();
		for (Document d : saved) {
			if (d.getSellerName() != null && !d.getSellerName().isBlank() && d.getTotalAmount() != null) {
				byShop.computeIfAbsent(d.getSellerName().strip().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(d);
			}
		}
		return byShop.values().stream()
				.map(bills -> new DashboardResponse.ShopSpend(latestSpelling(bills), sum(bills), bills.size()))
				.sorted(Comparator.comparing(DashboardResponse.ShopSpend::amount).reversed()
						.thenComparing(DashboardResponse.ShopSpend::name, String.CASE_INSENSITIVE_ORDER))
				.limit(TOP)
				.toList();
	}

	private static String latestSpelling(List<Document> bills) {
		Comparator<Document> newest = Comparator
				.comparing(Document::getPurchaseDate, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
				.thenComparing(Document::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
		return bills.stream().max(newest).map(d -> d.getSellerName().strip()).orElseThrow();
	}

	/** Sum of the bills' totals (bills without a total add nothing), always with 2 decimals. */
	private static BigDecimal sum(List<Document> bills) {
		return bills.stream().map(Document::getTotalAmount).filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
	}

	private static long count(List<WarrantyView> views, WarrantyStatus status) {
		return views.stream().filter(v -> v.status() == status).count();
	}
}
