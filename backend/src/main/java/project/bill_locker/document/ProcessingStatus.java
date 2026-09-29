package project.bill_locker.document;

/**
 * Pipeline: UPLOADED → PROCESSING → REVIEW_REQUIRED (or PROCESSED) → CONFIRMED,
 * or FAILED (can be reprocessed).
 */
public enum ProcessingStatus {
	UPLOADED,
	PROCESSING,
	PROCESSED,
	REVIEW_REQUIRED,
	CONFIRMED,
	FAILED;

	public boolean isInProgress() {
		return this == UPLOADED || this == PROCESSING;
	}

	public boolean needsReview() {
		return this == REVIEW_REQUIRED || this == PROCESSED;
	}
}
