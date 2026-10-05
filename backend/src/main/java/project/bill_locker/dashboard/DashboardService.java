package project.bill_locker.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.dashboard.DashboardSummary.CategorySpending;
import project.bill_locker.dashboard.DashboardSummary.WarrantyStats;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentRepository;
import project.bill_locker.document.DocumentSummary;
import project.bill_locker.document.ProcessingStatus;
import project.bill_locker.product.Category;
import project.bill_locker.product.Product;
import project.bill_locker.product.ProductRepository;
import project.bill_locker.service.ServiceRecord;
import project.bill_locker.service.ServiceRecordRepository;
import project.bill_locker.service.ServiceRecordResponse;
import project.bill_locker.warranty.WarrantyResponse;
import project.bill_locker.warranty.WarrantyService;
import project.bill_locker.warranty.WarrantyStatus;

/**
 * Builds the dashboard: it loads the user's products (with warranties) and documents
 * once, then counts and adds up in Java. Simple, and fast enough for one person's locker.
 */
@Service
public class DashboardService {

	/** Warranties ending within this many days are listed as "upcoming". */
	private static final int UPCOMING_DAYS = 90;
	/** Services due within this many days (or overdue) are listed as "upcoming". */
	private static final int UPCOMING_SERVICE_DAYS = 60;
	private static final int RECENT_DOCUMENTS = 5;

	private final ProductRepository products;
	private final DocumentRepository documents;
	private final ServiceRecordRepository serviceRecords;

	public DashboardService(ProductRepository products, DocumentRepository documents,
			ServiceRecordRepository serviceRecords) {
		this.products = products;
		this.documents = documents;
		this.serviceRecords = serviceRecords;
	}

	@Transactional(readOnly = true)
	public DashboardSummary summary(UUID userId) {
		LocalDate today = LocalDate.now();
		List<Product> userProducts = products.findForUser(userId, null, null);
		List<Document> userDocuments = documents.findForUser(userId, null, null, null); // newest first
		List<WarrantyResponse> warranties = userProducts.stream()
				.map(Product::getWarranty)
				.filter(Objects::nonNull)
				.map(warranty -> WarrantyResponse.of(warranty, today))
				.toList();

		return new DashboardSummary(
				userProducts.size(),
				userDocuments.size(),
				userDocuments.stream().filter(DashboardService::waitsForReview).count(),
				totalSpending(userProducts),
				"INR", // like the contract, the totals assume one currency
				countByStatus(warranties),
				spendingByCategory(userProducts),
				upcomingExpirations(warranties),
				upcomingServices(serviceRecords.latestPerProduct(userId).values(), today),
				userDocuments.stream().limit(RECENT_DOCUMENTS).map(DocumentSummary::from).toList());
	}

	/** Read, but not confirmed yet: the user should check the details. */
	private static boolean waitsForReview(Document document) {
		ProcessingStatus status = document.getProcessingStatus();
		return status == ProcessingStatus.PROCESSED || status == ProcessingStatus.REVIEW_REQUIRED;
	}

	private static BigDecimal totalSpending(List<Product> products) {
		return products.stream()
				.map(Product::getPurchasePrice)
				.filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static WarrantyStats countByStatus(List<WarrantyResponse> warranties) {
		Map<WarrantyStatus, Long> counts = new LinkedHashMap<>();
		warranties.forEach(warranty -> counts.merge(warranty.summary().status(), 1L, Long::sum));
		return new WarrantyStats(warranties.size(),
				counts.getOrDefault(WarrantyStatus.ACTIVE, 0L),
				counts.getOrDefault(WarrantyStatus.EXPIRING_SOON, 0L),
				counts.getOrDefault(WarrantyStatus.EXPIRED, 0L),
				counts.getOrDefault(WarrantyStatus.UNKNOWN, 0L));
	}

	/** Purchase prices added up per category, biggest first. Products without a category count as "Other". */
	private static List<CategorySpending> spendingByCategory(List<Product> products) {
		Map<String, CategorySpending> byName = new LinkedHashMap<>();
		for (Product product : products) {
			if (product.getPurchasePrice() == null) {
				continue;
			}
			Category category = product.getCategory();
			String name = category == null ? "Other" : category.getName();
			String slug = category == null ? null : category.getSlug();
			byName.merge(name, new CategorySpending(name, slug, product.getPurchasePrice()),
					(sum, more) -> new CategorySpending(name, sum.categorySlug(), sum.amount().add(more.amount())));
		}
		return byName.values().stream()
				.sorted(Comparator.comparing(CategorySpending::amount).reversed())
				.toList();
	}

	/**
	 * One per product: the next service from its most recent record, if it is due within
	 * 60 days or already overdue. Soonest first.
	 */
	private static List<ServiceRecordResponse> upcomingServices(Collection<ServiceRecord> latestRecords,
			LocalDate today) {
		return latestRecords.stream()
				.filter(record -> record.getNextServiceDate() != null
						&& ChronoUnit.DAYS.between(today, record.getNextServiceDate()) <= UPCOMING_SERVICE_DAYS)
				.sorted(Comparator.comparing(ServiceRecord::getNextServiceDate))
				.map(ServiceRecordResponse::from)
				.toList();
	}

	/** Not expired yet, and ending within 90 days: soonest first. */
	private static List<WarrantyResponse> upcomingExpirations(List<WarrantyResponse> warranties) {
		return warranties.stream()
				.filter(warranty -> {
					Long daysLeft = warranty.summary().daysRemaining();
					return daysLeft != null && daysLeft >= 0 && daysLeft <= UPCOMING_DAYS;
				})
				.sorted(WarrantyService.SOONEST_EXPIRY_FIRST)
				.toList();
	}
}
