package project.bill_locker.warranty;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentItem;
import project.bill_locker.document.DocumentRepository;

/** Every product on the user's saved bills, with how its warranty stands today. */
@Service
public class WarrantyService {

	/** Expiring soon first, then active, expired and unknown: what needs attention comes first. */
	private static final List<WarrantyStatus> GROUP_ORDER = List.of(WarrantyStatus.EXPIRING_SOON,
			WarrantyStatus.ACTIVE, WarrantyStatus.EXPIRED, WarrantyStatus.NO_INFO);

	private final DocumentRepository documents;
	private final Clock clock;

	public WarrantyService(DocumentRepository documents, Clock clock) {
		this.documents = documents;
		this.clock = clock;
	}

	/** All products of all saved bills, in the "All" tab's order. Also used by the dashboard. */
	@Transactional(readOnly = true)
	public List<WarrantyView> views(UUID userId) {
		LocalDate today = LocalDate.now(clock);
		return documents.findSavedWithItems(userId).stream()
				.flatMap(document -> document.getItems().stream().map(item -> view(document, item, today)))
				.sorted(ORDER)
				.toList();
	}

	/** The warranties page: {@code q} narrows the counts and the rows, {@code status} only the rows. */
	@Transactional(readOnly = true)
	public WarrantyList list(UUID userId, WarrantyStatus status, String q) {
		String search = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
		List<WarrantyView> matching = views(userId).stream().filter(v -> matches(v, search)).toList();
		List<WarrantyView> rows = status == null ? matching
				: matching.stream().filter(v -> v.status() == status).toList();
		return new WarrantyList(WarrantyList.Counts.of(matching), rows);
	}

	private static WarrantyView view(Document document, DocumentItem item, LocalDate today) {
		LocalDate end = WarrantyRules.effectiveEnd(item.getWarrantyEndDate(), item.getWarrantyStartDate(),
				item.getWarrantyPeriodMonths());
		return new WarrantyView(document.getId(), item.getProductName(), item.getModelNumber(), item.getSerialNumber(),
				document.getSellerName(), item.getWarrantyProvider(), document.getPurchaseDate(),
				item.getWarrantyStartDate(), end, WarrantyRules.daysLeft(end, today), WarrantyRules.status(end, today),
				item.getRegistrationUrl());
	}

	private static boolean matches(WarrantyView v, String search) {
		if (search.isEmpty()) {
			return true;
		}
		return Stream.of(v.productName(), v.modelNumber(), v.serialNumber(), v.sellerName(), v.warrantyProvider())
				.anyMatch(text -> text != null && text.toLowerCase(Locale.ROOT).contains(search));
	}

	/**
	 * Within a group: expiring and active by end date, soonest first; expired most
	 * recent first; unknown by purchase date, newest first. Then by product name.
	 */
	private static final Comparator<WarrantyView> ORDER = Comparator
			.comparingInt((WarrantyView v) -> GROUP_ORDER.indexOf(v.status()))
			.thenComparing((a, b) -> switch (a.status()) {
				case EXPIRING_SOON, ACTIVE -> a.endDate().compareTo(b.endDate());
				case EXPIRED -> b.endDate().compareTo(a.endDate());
				case NO_INFO -> Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())
						.compare(a.purchaseDate(), b.purchaseDate());
			})
			.thenComparing(WarrantyView::productName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
}
