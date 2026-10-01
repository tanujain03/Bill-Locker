package project.bill_locker.document;

/** Which part of the reading is running while a document is PROCESSING (shown as progress in the app). */
public enum ProcessingStage {
	/** Getting the text out of the file: PDF text, or OCR for photos and scans. */
	OCR,
	/** Finding the details (date, total, invoice number …) in that text. */
	EXTRACTION
}
