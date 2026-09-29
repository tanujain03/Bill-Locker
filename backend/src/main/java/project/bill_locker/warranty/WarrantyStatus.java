package project.bill_locker.warranty;

/** Computed from the expiry date and "today" — never stored, so it can never go stale. */
public enum WarrantyStatus {
	ACTIVE,
	EXPIRING_SOON,
	EXPIRED,
	UNKNOWN
}
