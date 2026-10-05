package project.bill_locker.notification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.product.Product;
import project.bill_locker.product.ProductRepository;
import project.bill_locker.service.ServiceRecord;
import project.bill_locker.service.ServiceRecordRepository;
import project.bill_locker.warranty.Warranty;
import project.bill_locker.warranty.WarrantyDates;

/**
 * The reminder rules (docs/api-contract.md §10). {@link ReminderJob} runs them every day.
 * Running them twice on the same day changes nothing: each reminder has a dedupe key.
 */
@Service
public class ReminderService {

	/** Warranties that ended up to 60 days ago still get an "expired" reminder. */
	static final int RECENTLY_EXPIRED_DAYS = 60;
	/** A service gets a reminder from 7 days before it is due… */
	static final int SERVICE_DUE_DAYS = 7;
	/** …until 30 days after (overdue). */
	static final int SERVICE_OVERDUE_DAYS = 30;

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final ProductRepository products;
	private final ServiceRecordRepository serviceRecords;
	private final NotificationRepository notifications;

	public ReminderService(ProductRepository products, ServiceRecordRepository serviceRecords,
			NotificationRepository notifications) {
		this.products = products;
		this.serviceRecords = serviceRecords;
		this.notifications = notifications;
	}

	/** Creates the reminders one user should have on {@code today}; returns how many are new. */
	@Transactional
	public int createReminders(UUID userId, LocalDate today) {
		int created = 0;
		for (Product product : products.findForUser(userId, null, null)) {
			Warranty warranty = product.getWarranty();
			if (warranty == null || warranty.getExpiryDate() == null) {
				continue; // unknown warranty: nothing to remind about
			}
			long daysLeft = warranty.daysRemainingOn(today);
			String key = warranty.getId() + ":" + warranty.getExpiryDate();
			if (daysLeft >= 0 && daysLeft <= WarrantyDates.EXPIRING_SOON_DAYS) {
				created += add(userId, product, NotificationType.WARRANTY_EXPIRING, "Warranty expiring soon",
						"Your %s warranty expires %s (%s).".formatted(product.getName(), inDays(daysLeft),
								DATE.format(warranty.getExpiryDate())), key);
			}
			else if (daysLeft < 0 && daysLeft >= -RECENTLY_EXPIRED_DAYS) {
				created += add(userId, product, NotificationType.WARRANTY_EXPIRED, "Warranty expired",
						"The warranty for your %s ended on %s.".formatted(product.getName(),
								DATE.format(warranty.getExpiryDate())), key);
			}
		}
		// Only each product's latest record counts: an older record's "next service" has been done.
		for (ServiceRecord record : serviceRecords.latestPerProduct(userId).values()) {
			LocalDate due = record.getNextServiceDate();
			if (due == null) {
				continue;
			}
			long days = ChronoUnit.DAYS.between(today, due);
			if (days > SERVICE_DUE_DAYS || days < -SERVICE_OVERDUE_DAYS) {
				continue;
			}
			Product product = record.getProduct();
			created += days < 0
					? add(userId, product, NotificationType.SERVICE_DUE, "Service overdue",
							"Your %s service was due on %s.".formatted(product.getName(), DATE.format(due)),
							record.getId() + ":" + due)
					: add(userId, product, NotificationType.SERVICE_DUE, "Service due soon",
							"Your %s service is due %s (%s).".formatted(product.getName(), inDays(days), DATE.format(due)),
							record.getId() + ":" + due);
		}
		return created;
	}

	/** Saves the notification unless the user already has one with this key; returns 1 if saved. */
	private int add(UUID userId, Product product, NotificationType type, String title, String message, String key) {
		String dedupeKey = type + ":" + key;
		if (notifications.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
			return 0;
		}
		notifications.save(new Notification(product.getUser(), product, null, type, title, message, dedupeKey));
		return 1;
	}

	private static String inDays(long days) {
		if (days == 0) {
			return "today";
		}
		return days == 1 ? "tomorrow" : "in " + days + " days";
	}
}
