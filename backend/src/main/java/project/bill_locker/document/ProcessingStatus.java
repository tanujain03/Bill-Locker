package project.bill_locker.document;

/**
 * Where a document is in the pipeline: UPLOADED → PROCESSING → PROCESSED (details
 * found, waiting for the user's review) → CONFIRMED (saved as a product), or FAILED.
 * REVIEW_REQUIRED is the contract's other name for "waiting for review"; this
 * backend uses PROCESSED.
 */
public enum ProcessingStatus {
	UPLOADED,
	PROCESSING,
	PROCESSED,
	REVIEW_REQUIRED,
	CONFIRMED,
	FAILED
}
