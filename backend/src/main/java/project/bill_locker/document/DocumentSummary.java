package project.bill_locker.document;

import java.time.Instant;
import java.util.UUID;
import project.bill_locker.product.Product;

/**
 * A document as the API returns it in lists (docs/api-contract.md §8,
 * "DocumentSummary"). Gmail import comes in a later step, so the source is always "UPLOAD".
 */
public record DocumentSummary(
		UUID id,
		UUID productId,
		String productName,
		DocumentType documentType,
		String fileName,
		String mimeType,
		long fileSize,
		ProcessingStatus processingStatus,
		ProcessingStage processingStage,
		String source,
		String errorMessage,
		Instant createdAt,
		Instant updatedAt) {

	public static DocumentSummary from(Document document) {
		Product product = document.getProduct();
		return new DocumentSummary(document.getId(), product == null ? null : product.getId(),
				product == null ? null : product.getName(), document.getDocumentType(), document.getFileName(),
				document.getMimeType(), document.getFileSize(), document.getProcessingStatus(),
				document.getProcessingStage(), "UPLOAD", document.getErrorMessage(), document.getCreatedAt(),
				document.getUpdatedAt());
	}
}
