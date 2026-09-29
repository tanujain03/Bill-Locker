package project.bill_locker.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.BaseEntity;
import project.bill_locker.document.Document;
import project.bill_locker.product.Product;
import project.bill_locker.user.User;

/**
 * An in-app notification. Reminders created by the scheduled job carry a
 * {@link #dedupeKey} (e.g. {@code WARRANTY_EXPIRING:<warrantyId>:<expiryDate>});
 * the unique (user, dedupe key) constraint stops the same reminder being sent twice.
 */
@Entity
@Table(name = "notifications",
		uniqueConstraints = @UniqueConstraint(name = "uk_notifications_dedupe", columnNames = {"user_id", "dedupe_key"}),
		indexes = {
				@Index(name = "idx_notifications_user_scheduled", columnList = "user_id, scheduled_at DESC"),
				@Index(name = "idx_notifications_user_read", columnList = "user_id, is_read")
		})
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** Notifications about a deleted product disappear with it. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_notifications_product"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "document_id", foreignKey = @ForeignKey(name = "fk_notifications_document"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Document document;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 30)
	private NotificationType type;

	@NotBlank
	@Size(max = 120)
	@Column(name = "title", nullable = false, length = 120)
	private String title;

	@NotBlank
	@Size(max = 500)
	@Column(name = "message", nullable = false, length = 500)
	private String message;

	/** Visible from this moment on (lets the job schedule reminders ahead of time). */
	@Column(name = "scheduled_at", nullable = false)
	private Instant scheduledAt;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Column(name = "read_at")
	private Instant readAt;

	@Size(max = 160)
	@Column(name = "dedupe_key", length = 160)
	private String dedupeKey;

	public Notification(User user, NotificationType type, String title, String message) {
		this.user = user;
		this.type = type;
		this.title = title;
		this.message = message;
	}

	@PrePersist
	void defaultScheduledAt() {
		if (scheduledAt == null) {
			scheduledAt = Instant.now();
		}
	}

	public void markRead() {
		if (!read) {
			read = true;
			readAt = Instant.now();
		}
	}
}
