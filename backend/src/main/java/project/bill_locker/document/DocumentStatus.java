package project.bill_locker.document;

/**
 * Where a document is in its life:
 * UPLOADED (file stored, not read yet) → EXTRACTED (the AI filled the details, the
 * user hasn't checked them) → SAVED (the user reviewed and saved them).
 */
public enum DocumentStatus {
	UPLOADED, EXTRACTED, SAVED
}
