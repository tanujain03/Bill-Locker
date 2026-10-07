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
		Instant createdAt) {

	static DocumentSummary of(Document d) {
		var items = d.getItems();
		return new DocumentSummary(d.getId(), d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getStatus(),
				d.getDocumentType(), d.getDocumentNumber(), d.getSellerName(), d.getPurchaseDate(), d.getTotalAmount(),
				items.size(), items.isEmpty() ? null : items.getFirst().getProductName(), d.getCreatedAt());
	}
}
