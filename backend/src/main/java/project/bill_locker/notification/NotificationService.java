package project.bill_locker.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.Document;
import project.bill_locker.user.User;

/** The signed-in user's notifications, and the "document ready" notice the reader sends. */
@Service
public class NotificationService {

	private final NotificationRepository notifications;

	public NotificationService(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	/** Newest first. */
	@Transactional(readOnly = true)
	public List<NotificationResponse> list(UUID userId) {
		return notifications.findByUserIdOrderByScheduledAtDesc(userId).stream().map(NotificationResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public long unreadCount(UUID userId) {
		return notifications.countByUserIdAndReadFalse(userId);
	}

	@Transactional
	public NotificationResponse markRead(UUID userId, UUID notificationId) {
		Notification notification = notifications.findByIdAndUserId(notificationId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
						"The notification was not found."));
		notification.markRead();
		return NotificationResponse.from(notification);
	}

	@Transactional
	public void markAllRead(UUID userId) {
		notifications.markAllRead(userId);
	}

	/** Called after a Gmail scan that found likely bills (an event, so no dedupe key). */
	@Transactional
	public void gmailBillsFound(User user, int count) {
		notifications.save(new Notification(user, null, null, NotificationType.GMAIL_BILLS_FOUND,
				"New bills found in Gmail",
				"Found " + count + (count == 1 ? " bill" : " bills") + " in your inbox. Review and import them.", null));
	}

	/** Called by the reader when it has found a document's details (an event, so no dedupe key). */
	@Transactional
	public void documentProcessed(Document document) {
		notifications.save(new Notification(document.getUser(), document.getProduct(), document,
				NotificationType.DOCUMENT_PROCESSED, "Document ready for review",
				"We’ve finished reading " + document.getFileName() + ". Review the details to save it to your locker.",
				null));
	}
}
