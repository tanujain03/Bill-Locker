package project.bill_locker.document;

/**
 * Where a document is in the reading pipeline. For now every document stays
 * UPLOADED; OCR and AI extraction (next steps) move it through
 * UPLOADED → PROCESSING → REVIEW_REQUIRED → CONFIRMED, or FAILED.
 */
public enum ProcessingStatus {
	UPLOADED,
	PROCESSING,
	PROCESSED,
	REVIEW_REQUIRED,
	CONFIRMED,
	FAILED
}
