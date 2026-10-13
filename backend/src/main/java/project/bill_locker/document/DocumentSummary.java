package project.bill_locker.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One row in the documents list: just enough to recognise a bill. */
public record DocumentSummary(
		UUID id,
		String fileName,
		String contentType,
		long sizeBytes,
		DocumentStatus status,
		DocumentType documentType,
		String documentNumber,
		String sellerName,
		LocalDate purchaseDate,
		BigDecimal totalAmount,
		int itemCount,
		String firstProductName,
		BillCategory category,
		Instant createdAt,
		boolean readQueued,
		String readError,
		String sourceGmail,
		/** A demo bill from "Try with sample data". */
		boolean sample,
		/** Looks like a copy of another document (see DuplicateRules). */
		boolean possibleDuplicate) {

	/** For short lists (dashboard, expenses) that don't check for copies. */
	public static DocumentSummary of(Document d) {
		return of(d, false);
	}

	public static DocumentSummary of(Document d, boolean possibleDuplicate) {
		var items = d.getItems();
		return new DocumentSummary(d.getId(), d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getStatus(),
				d.getDocumentType(), d.getDocumentNumber(), d.getSellerName(), d.getPurchaseDate(), d.getTotalAmount(),
				items.size(), items.isEmpty() ? null : items.getFirst().getProductName(), BillCategory.parse(d.getCategory()),
				d.getCreatedAt(),
				d.getReadQueuedAt() != null, d.getReadError(), d.getSourceGmail(), d.isSample(), possibleDuplicate);
	}
}
