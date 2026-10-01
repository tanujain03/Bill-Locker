package project.bill_locker.warranty;

/** Worked out from the expiry date and today's date. Never stored, so it can't go out of date. */
public enum WarrantyStatus {
	ACTIVE,
	/** 0 to 30 days left. */
	EXPIRING_SOON,
	EXPIRED,
	/** The warranty period or the purchase date is not known. */
	UNKNOWN
}
