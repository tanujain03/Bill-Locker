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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.BaseEntity;
import project.bill_locker.document.Document;
import project.bill_locker.product.Product;
import project.bill_locker.user.User;

/**
 * A message in the bell menu. Reminders carry a {@code dedupeKey} such as
 * {@code WARRANTY_EXPIRING:<warranty id>:<expiry date>}; the unique (user, key)
 * constraint makes sure the daily job never sends the same reminder twice.
 */
@Entity
@Table(name = "notifications",
		uniqueConstraints = @UniqueConstraint(name = "uk_notifications_dedupe", columnNames = {"user_id", "dedupe_key"}),
		indexes = @Index(name = "idx_notifications_user_scheduled", columnList = "user_id, scheduled_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** What it is about. Notifications about a deleted product or document go with it (CASCADE). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_notifications_product"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "document_id", foreignKey = @ForeignKey(name = "fk_notifications_document"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Document document;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 30)
	private NotificationType type;

	@Column(name = "title", nullable = false, length = 120)
	private String title;

	@Column(name = "message", nullable = false, length = 500)
	private String message;

	/** When it shows up. For now always when it was created. */
	@Column(name = "scheduled_at", nullable = false)
	private Instant scheduledAt;

	/** "read" alone is a risky column name in SQL, hence is_read. */
	@Column(name = "is_read", nullable = false)
	private boolean read;

	/** Null for one-off events (e.g. "document ready"); see the class comment. */
	@Column(name = "dedupe_key", length = 160)
	private String dedupeKey;

	public Notification(User user, Product product, Document document, NotificationType type, String title,
			String message, String dedupeKey) {
		this.user = user;
		this.product = product;
		this.document = document;
		this.type = type;
		this.title = title;
		this.message = message;
		this.dedupeKey = dedupeKey;
		this.scheduledAt = Instant.now();
	}

	public void markRead() {
		read = true;
	}
}
