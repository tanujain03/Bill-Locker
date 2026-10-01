package project.bill_locker.document;

import java.time.Instant;
import java.util.UUID;

/**
 * A document as the API returns it in lists (docs/api-contract.md §8,
 * "DocumentSummary"). Products, processing stages and Gmail import come in later
 * steps, so for now those fields are always null, or "UPLOAD" for the source.
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
		String processingStage,
		String source,
		String errorMessage,
		Instant createdAt,
		Instant updatedAt) {

	static DocumentSummary from(Document document) {
		return new DocumentSummary(document.getId(), null, null, document.getDocumentType(), document.getFileName(),
				document.getMimeType(), document.getFileSize(), document.getProcessingStatus(), null, "UPLOAD", null,
				document.getCreatedAt(), document.getUpdatedAt());
	}
}
