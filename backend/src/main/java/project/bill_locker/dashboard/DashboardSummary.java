package project.bill_locker.dashboard;

import java.math.BigDecimal;
import java.util.List;
import project.bill_locker.document.DocumentSummary;
import project.bill_locker.service.ServiceRecordResponse;
import project.bill_locker.warranty.WarrantyResponse;

/** Everything the dashboard shows, in one response (docs/api-contract.md §7). */
public record DashboardSummary(
		int totalProducts,
		int totalDocuments,
		long documentsToReview,
		BigDecimal totalSpending,
		String currency,
		WarrantyStats warranties,
		List<CategorySpending> spendingByCategory,
		List<WarrantyResponse> upcomingExpirations,
		List<ServiceRecordResponse> upcomingServices,
		List<DocumentSummary> recentDocuments) {

	/** How many warranties there are in each status. */
	public record WarrantyStats(long total, long active, long expiringSoon, long expired, long unknown) {
	}

	public record CategorySpending(String categoryName, String categorySlug, BigDecimal amount) {
	}
}
