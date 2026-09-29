package project.bill_locker.document;

/** Finer progress while a document is PROCESSING (shown as steps in the UI). */
public enum ProcessingStage {
	OCR,
	EXTRACTION,
	INDEXING
}
