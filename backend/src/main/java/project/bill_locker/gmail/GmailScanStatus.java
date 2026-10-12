package project.bill_locker.gmail;

/** Where an account's mailbox scan is: waiting, running, done (IDLE) or failed (ERROR). */
public enum GmailScanStatus {
	IDLE, QUEUED, SCANNING, ERROR
}
