package project.bill_locker.gmail;

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
import project.bill_locker.user.User;

/**
 * An email found by a scan, stored in {@code gmail_emails}. Only sender, subject,
 * Gmail's short preview and date are kept, never the body.
 */
@Entity
@Table(name = "gmail_emails",
		uniqueConstraints = @UniqueConstraint(name = "uk_gmail_emails_account_message",
				columnNames = {"account_id", "gmail_message_id"}),
		indexes = @Index(name = "idx_gmail_emails_user_received", columnList = "user_id, received_at desc"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GmailEmail extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_emails_account"))
	@OnDelete(action = OnDeleteAction.CASCADE) // disconnecting an account removes its emails
	private GmailAccount account;

	// Also stored here so every lookup can be "by id and user", like elsewhere.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_emails_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@Column(name = "gmail_message_id", nullable = false, length = 64)
	private String gmailMessageId;

	@Column(name = "from_name", length = 200)
	private String fromName;

	@Column(name = "from_email", length = 254)
	private String fromEmail;

	@Column(name = "subject", length = 500)
	private String subject;

	@Column(name = "snippet", length = 500)
	private String snippet;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 20)
	private EmailKind kind;

	public GmailEmail(GmailAccount account, String gmailMessageId, String fromName, String fromEmail, String subject,
			String snippet, Instant receivedAt, EmailKind kind) {
		this.account = account;
		this.user = account.getUser();
		this.gmailMessageId = gmailMessageId;
		this.fromName = fromName;
		this.fromEmail = fromEmail;
		this.subject = subject;
		this.snippet = snippet;
		this.receivedAt = receivedAt;
		this.kind = kind;
	}
}
