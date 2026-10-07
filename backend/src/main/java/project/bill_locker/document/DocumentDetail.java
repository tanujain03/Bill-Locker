package project.bill_locker.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Everything about one document, sent to the document page. */
public record DocumentDetail(
		UUID id,
		String fileName,
		String contentType,
		long sizeBytes,
		DocumentStatus status,
		/** The CSV's document_url: where the file can be downloaded (needs the login token). */
		String documentUrl,
		DocumentType documentType,
		String documentNumber,
		String sellerName,
		String sellerAddress,
		String sellerContact,
		String buyerName,
		String buyerAddress,
		String buyerEmail,
		LocalDate purchaseDate,
		BigDecimal taxAmount,
		BigDecimal totalAmount,
		List<DocumentItemView> items,
		Instant createdAt,
		Instant updatedAt,
		boolean readQueued,
		String readError,
		String sourceGmail) {

	static DocumentDetail of(Document d) {
		return new DocumentDetail(d.getId(), d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getStatus(),
				"/api/documents/" + d.getId() + "/download",
				d.getDocumentType(), d.getDocumentNumber(),
				d.getSellerName(), d.getSellerAddress(), d.getSellerContact(),
				d.getBuyerName(), d.getBuyerAddress(), d.getBuyerEmail(),
				d.getPurchaseDate(), d.getTaxAmount(), d.getTotalAmount(),
				d.getItems().stream().map(DocumentItemView::of).toList(),
				d.getCreatedAt(), d.getUpdatedAt(),
				d.getReadQueuedAt() != null, d.getReadError(), d.getSourceGmail());
	}
}
