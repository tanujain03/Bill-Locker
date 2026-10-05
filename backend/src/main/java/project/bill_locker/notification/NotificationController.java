package project.bill_locker.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/** Notification endpoints (docs/api-contract.md §10), used by the bell and the Notifications page. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	/** Body of the unread count: {@code { "count": 4 }}. */
	public record UnreadCount(long count) {
	}

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	/** {@code GET /api/notifications} — newest first. */
	@GetMapping
	public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return notificationService.list(CurrentUser.id(jwt));
	}

	/** {@code GET /api/notifications/unread-count} — the number on the bell; the app asks every 30 s. */
	@GetMapping("/unread-count")
	public UnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt) {
		return new UnreadCount(notificationService.unreadCount(CurrentUser.id(jwt)));
	}

	/** {@code PATCH /api/notifications/{id}/read} */
	@PatchMapping("/{id}/read")
	public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return notificationService.markRead(CurrentUser.id(jwt), id);
	}

	/** {@code POST /api/notifications/read-all} */
	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
		notificationService.markAllRead(CurrentUser.id(jwt));
	}
}
