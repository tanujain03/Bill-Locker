package project.bill_locker.warranty;

/** How a product's warranty stands today. Worked out on the fly, never stored. */
public enum WarrantyStatus {
	/** More than 30 days left. */
	ACTIVE,
	/** 0–30 days left (today counts). */
	EXPIRING_SOON,
	/** The end date has passed. */
	EXPIRED,
	/** No end date, and none can be worked out. */
	NO_INFO
}
