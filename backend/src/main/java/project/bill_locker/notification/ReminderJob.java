package project.bill_locker.notification;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.bill_locker.user.User;
import project.bill_locker.user.UserRepository;

/**
 * When the reminders are made: every morning at 08:00, and once when the app starts
 * (so a day isn't missed if the app was off at 08:00). The rules live in
 * {@link ReminderService}, a separate bean, so that its {@code @Transactional} works:
 * Spring adds transactions only to calls that come from another bean.
 */
@Component
public class ReminderJob {

	private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

	private final UserRepository users;
	private final ReminderService reminderService;
	private final boolean enabled;

	public ReminderJob(UserRepository users, ReminderService reminderService,
			@Value("${app.reminders.enabled:true}") boolean enabled) {
		this.users = users;
		this.reminderService = reminderService;
		this.enabled = enabled;
	}

	/** Cron fields: second minute hour day month weekday. */
	@Scheduled(cron = "0 0 8 * * *")
	public void everyMorning() {
		if (enabled) {
			runForEveryone();
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		if (enabled) {
			runForEveryone();
		}
	}

	/** One transaction per user, so one user's problem can't stop everyone else's reminders. */
	public void runForEveryone() {
		LocalDate today = LocalDate.now();
		int created = 0;
		for (User user : users.findAll()) {
			try {
				created += reminderService.createReminders(user.getId(), today);
			}
			catch (RuntimeException ex) {
				log.warn("Could not create reminders for user {}", user.getId(), ex);
			}
		}
		log.info("Reminders for {}: {} new", today, created);
	}
}
