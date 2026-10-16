package project.bill_locker.warranty;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentItem;
import project.bill_locker.document.DocumentItemRepository;
import project.bill_locker.document.DocumentRepository;

/**
 * Builds a product's warranty claim kit. Nothing is sent anywhere: the user reads the kit,
 * and sends the claim email from their own email app.
 */
@Service
public class ClaimKitService {

	private final DocumentRepository documents;
	private final Clock clock;
	private final DocumentItemRepository items;

	public ClaimKitService(DocumentRepository documents, Clock clock, DocumentItemRepository items) {
		this.documents = documents;
		this.clock = clock;
		this.items = items;
	}

	/** Another user's bill, or a position the bill doesn't have, is a 404. */
	@Transactional(readOnly = true)
	public ClaimKit kit(UUID userId, UUID documentId, int position) {
		Document d = documents.findByIdAndUserId(documentId, userId).orElseThrow(ClaimKitService::notFound);
		DocumentItem item = d.getItems().stream().filter(i -> i.getPosition() == position).findFirst()
				.orElseThrow(ClaimKitService::notFound);

		return view(userId, d, item);
	}

	@Transactional(readOnly = true)
	public ClaimKit kit(UUID userId, UUID productId) {
		DocumentItem item = items.findByIdAndDocumentUserId(productId, userId).orElseThrow(ClaimKitService::notFound);
		return view(userId, item.getDocument(), item);
	}

	private ClaimKit view(UUID userId, Document d, DocumentItem item) {
		LocalDate today = LocalDate.now(clock);
		LocalDate end = WarrantyRules.effectiveEnd(item.getWarrantyEndDate(), item.getWarrantyStartDate(),
				item.getWarrantyPeriodMonths());
		// The brand names the support pages; without one, the product name is the best guess.
		String maker = firstNonBlank(item.getBrand(), item.getProductName(), "product");

		return new ClaimKit(d.getId(), item.getId(), item.getPosition(), d.getFileName(),
				item.getProductName(), item.getBrand(), item.getModelNumber(), item.getSerialNumber(), item.getUnitPrice(),
				d.getSellerName(), d.getSellerContact(), d.getSellerAddress(), d.getDocumentNumber(), d.getPurchaseDate(),
				d.getBuyerName(),
				item.getWarrantyProvider(), item.getWarrantyStartDate(), end, WarrantyRules.daysLeft(end, today),
				WarrantyRules.status(end, today),
				item.getRegistrationUrl(),
				"https://www.google.com/search?q=" + encode(maker + " customer care warranty claim"),
				"https://www.google.com/maps/search/?api=1&query=" + encode(maker + " authorised service centre near me"),
				missing(d, item, end));
	}

	/** The facts a service centre usually asks for, in the order the checklist shows them. */
	private static List<String> missing(Document d, DocumentItem item, LocalDate end) {
		List<String> missing = new ArrayList<>();
		if (blank(item.getProductName())) {
			missing.add("Product name");
		}
		if (blank(item.getModelNumber())) {
			missing.add("Model number");
		}
		if (blank(item.getSerialNumber())) {
			missing.add("Serial number");
		}
		if (d.getPurchaseDate() == null) {
			missing.add("Purchase date");
		}
		if (blank(d.getDocumentNumber())) {
			missing.add("Invoice number");
		}
		if (end == null) {
			missing.add("Warranty end date");
		}
		return missing;
	}

	private static String firstNonBlank(String... values) {
		for (String v : values) {
			if (!blank(v)) {
				return v.strip();
			}
		}
		return "";
	}

	private static boolean blank(String text) {
		return text == null || text.isBlank();
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "That product was not found.");
	}
}
