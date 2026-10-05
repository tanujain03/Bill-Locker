package project.bill_locker.notification;

/**
 * All the types from the contract, even GMAIL_BILLS_FOUND (Gmail import comes later):
 * Hibernate writes the allowed values into a CHECK constraint once and `ddl-auto=update`
 * never changes it, so adding a value later would need a database reset.
 */
public enum NotificationType {
	WARRANTY_EXPIRING,
	WARRANTY_EXPIRED,
	SERVICE_DUE,
	DOCUMENT_PROCESSED,
	GMAIL_BILLS_FOUND
}
