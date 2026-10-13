package project.bill_locker.document;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * When do two documents look like the same bill? (The same file twice is caught earlier,
 * by its content hash; these rules catch a photo and a PDF of one bill.)
 * Both need the same purchase date, and then either
 * - the same bill number (and no two different shops), or
 * - the same shop and the same total.
 * Names are compared without case, spaces and punctuation: "Croma Ltd." = "croma ltd".
 * A document the user marked "Keep both" never counts.
 */
final class DuplicateRules {

	private DuplicateRules() {
	}

	static boolean sameBill(Document a, Document b) {
		if (a.getId().equals(b.getId()) || a.isNotDuplicate() || b.isNotDuplicate()
				|| a.getPurchaseDate() == null || !a.getPurchaseDate().equals(b.getPurchaseDate())) {
			return false;
		}
		String sellerA = key(a.getSellerName());
		String sellerB = key(b.getSellerName());
		String numberA = key(a.getDocumentNumber());

		boolean sameNumber = numberA != null && numberA.equals(key(b.getDocumentNumber()))
				&& (sellerA == null || sellerB == null || sellerA.equals(sellerB));
		boolean sameShopAndTotal = sellerA != null && sellerA.equals(sellerB)
				&& a.getTotalAmount() != null && b.getTotalAmount() != null
				&& a.getTotalAmount().compareTo(b.getTotalAmount()) == 0; // 1499.0 = 1499.00
		return sameNumber || sameShopAndTotal;
	}

	/** The ids of all documents that look like a copy of another one in the list. */
	static Set<UUID> withCopies(List<Document> documents) {
		Set<UUID> ids = new HashSet<>();
		// Only documents on the same day can match, so compare within each day.
		Map<LocalDate, List<Document>> byDate = documents.stream()
				.filter(d -> d.getPurchaseDate() != null)
				.collect(Collectors.groupingBy(Document::getPurchaseDate));
		for (List<Document> sameDay : byDate.values()) {
			for (Document a : sameDay) {
				for (Document b : sameDay) {
					if (sameBill(a, b)) {
						ids.add(a.getId());
					}
				}
			}
		}
		return ids;
	}

	/** "Croma Ltd." → "cromaltd"; blank → null. */
	private static String key(String text) {
		if (text == null) {
			return null;
		}
		String key = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
		return key.isEmpty() ? null : key;
	}

	/** The documents in {@code others} that look like a copy of {@code document}. */
	static List<Document> copiesOf(Document document, List<Document> others) {
		return others.stream().filter(other -> sameBill(document, other)).toList();
	}
}
