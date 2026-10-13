package project.bill_locker.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Another document that looks like the same bill (shown in the "possible copy" warning). */
public record DuplicateMatch(UUID id, String fileName, String sellerName, String documentNumber,
		LocalDate purchaseDate, BigDecimal totalAmount, DocumentStatus status, Instant createdAt) {

	static DuplicateMatch of(Document d) {
		return new DuplicateMatch(d.getId(), d.getFileName(), d.getSellerName(), d.getDocumentNumber(),
				d.getPurchaseDate(), d.getTotalAmount(), d.getStatus(), d.getCreatedAt());
	}
}
