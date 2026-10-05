package project.bill_locker.gmail;

/** IDLE between scans, SYNCING while the scanner reads the inbox, ERROR when the last scan failed. */
public enum GmailSyncStatus {
	IDLE,
	SYNCING,
	ERROR
}
