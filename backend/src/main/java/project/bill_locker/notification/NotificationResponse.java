package project.bill_locker.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification as the API returns it (docs/api-contract.md §10, "AppNotification").
 * The app opens the document (DOCUMENT_PROCESSED) or the product when it is clicked.
 */
public record NotificationResponse(
		UUID id,
		UUID productId,
		UUID documentId,
		NotificationType type,
		String title,
		String message,
		Instant scheduledAt,
		boolean read,
		Instant createdAt) {

	static NotificationResponse from(Notification notification) {
		return new NotificationResponse(notification.getId(),
				notification.getProduct() == null ? null : notification.getProduct().getId(),
				notification.getDocument() == null ? null : notification.getDocument().getId(),
				notification.getType(), notification.getTitle(), notification.getMessage(),
				notification.getScheduledAt(), notification.isRead(), notification.getCreatedAt());
	}
}
