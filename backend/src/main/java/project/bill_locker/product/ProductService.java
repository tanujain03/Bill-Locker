package project.bill_locker.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.BillCategory;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentItem;
import project.bill_locker.document.DocumentItemRepository;
import project.bill_locker.document.DocumentRepository;
import project.bill_locker.document.DocumentSummary;
import project.bill_locker.warranty.WarrantyRules;

/** Builds a product's page from its bill (same warranty rules as the Warranties page). */
@Service
public class ProductService {

	private final DocumentRepository documents;
	private final Clock clock;
	private final DocumentItemRepository items;

	public ProductService(DocumentRepository documents, Clock clock, DocumentItemRepository items) {
		this.documents = documents;
		this.clock = clock;
		this.items = items;
	}

	/** Another user's bill, or a position the bill doesn't have, is a 404. */
	@Transactional(readOnly = true)
	public ProductView product(UUID userId, UUID documentId, int position) {
		Document d = documents.findByIdAndUserId(documentId, userId).orElseThrow(ProductService::notFound);
		DocumentItem item = d.getItems().stream().filter(i -> i.getPosition() == position).findFirst()
				.orElseThrow(ProductService::notFound);

		return view(userId, d, item);
	}

	@Transactional(readOnly = true)
	public ProductView product(UUID userId, UUID productId) {
		DocumentItem item = items.findByIdAndDocumentUserId(productId, userId).orElseThrow(ProductService::notFound);
		return view(userId, item.getDocument(), item);
	}

	private ProductView view(UUID userId, Document d, DocumentItem item) {
		LocalDate today = LocalDate.now(clock);
		LocalDate end = WarrantyRules.effectiveEnd(item.getWarrantyEndDate(), item.getWarrantyStartDate(),
				item.getWarrantyPeriodMonths());
		LocalDate bought = d.getPurchaseDate();
		Long ownedDays = bought == null ? null : Math.max(0, ChronoUnit.DAYS.between(bought, today));

		return new ProductView(d.getId(), item.getId(), item.getPosition(),
				item.getProductName(), item.getBrand(), item.getModelNumber(), item.getSerialNumber(), item.getUnitPrice(),
				BillCategory.parse(d.getCategory()),
				d.getSellerName(), d.getDocumentNumber(), bought,
				item.getWarrantyProvider(), item.getWarrantyPeriodMonths(), item.getWarrantyStartDate(), end,
				WarrantyRules.daysLeft(end, today), WarrantyRules.status(end, today), item.getRegistrationUrl(), item.isWarrantyRegistered(),
				ownedDays, costPerMonth(item.getUnitPrice(), bought, today),
				DocumentSummary.of(d), related(userId, d, item), missing(d, item, end));
	}

	@Transactional
	public ProductView setRegistration(UUID userId, UUID documentId, int position, boolean registered) {
		Document d = documents.findByIdAndUserId(documentId, userId).orElseThrow(ProductService::notFound);
		DocumentItem item = d.getItems().stream().filter(i -> i.getPosition() == position).findFirst()
				.orElseThrow(ProductService::notFound);
		item.setWarrantyRegistered(registered);
		return product(userId, documentId, position);
	}

	@Transactional
	public ProductView setRegistration(UUID userId, UUID productId, boolean registered) {
		DocumentItem item = items.findByIdAndDocumentUserId(productId, userId).orElseThrow(ProductService::notFound);
		item.setWarrantyRegistered(registered);
		return view(userId, item.getDocument(), item);
	}

	/**
	 * What owning it has cost per month so far: the price spread over the whole months since the
	 * purchase, at least one (bought this month = the full price).
	 */
	static BigDecimal costPerMonth(BigDecimal price, LocalDate bought, LocalDate today) {
		if (price == null || bought == null) {
			return null;
		}
		long months = Math.max(1, ChronoUnit.MONTHS.between(bought, today));
		return price.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
	}

	/** Other documents with the same serial number (the strongest match) or the same model number. */
	private List<ProductView.RelatedDocument> related(UUID userId, Document bill, DocumentItem item) {
		String serial = key(item.getSerialNumber());
		String model = key(item.getModelNumber());
		if (serial.isEmpty() && model.isEmpty()) {
			return List.of();
		}
		List<ProductView.RelatedDocument> related = new ArrayList<>();
		for (Document other : documents.findMentioning(userId, bill.getId(), serial, model)) {
			boolean sameSerial = !serial.isEmpty()
					&& other.getItems().stream().anyMatch(i -> serial.equals(key(i.getSerialNumber())));
			related.add(new ProductView.RelatedDocument(DocumentSummary.of(other), sameSerial ? "serial number" : "model number"));
		}
		return related;
	}

	private static List<String> missing(Document d, DocumentItem item, LocalDate end) {
		List<String> missing = new ArrayList<>();
		if (blank(item.getModelNumber())) {
			missing.add("Model number");
		}
		if (blank(item.getSerialNumber())) {
			missing.add("Serial number");
		}
		if (item.getUnitPrice() == null) {
			missing.add("Price");
		}
		if (d.getPurchaseDate() == null) {
			missing.add("Purchase date");
		}
		if (end == null) {
			missing.add("Warranty end date");
		}
		return missing;
	}

	/** Serial and model numbers compare without case and surrounding spaces. */
	private static String key(String text) {
		return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
	}

	private static boolean blank(String text) {
		return text == null || text.isBlank();
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "That product was not found.");
	}
}
