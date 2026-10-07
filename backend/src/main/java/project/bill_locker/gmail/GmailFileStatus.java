package project.bill_locker.gmail;

/** An attachment's life: NEW (found) -> IMPORTING -> IMPORTED, or IGNORED / FAILED. */
public enum GmailFileStatus {
	NEW, IGNORED, IMPORTING, IMPORTED, FAILED
}
