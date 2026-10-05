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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.document.DocumentType;
import project.bill_locker.user.User;

/**
 * An email the scan shortlisted as a likely bill: who sent it, the subject, a short
 * preview and its attachments. Never the email's full text. Unique per user and Gmail
 * message id, so scanning again never adds the same email twice.
 */
@Entity
@Table(name = "gmail_messages",
		uniqueConstraints = @UniqueConstraint(name = "uk_gmail_messages_user_message", columnNames = {"user_id", "gmail_message_id"}),
		indexes = @Index(name = "idx_gmail_messages_user_received", columnList = "user_id, received_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GmailMessage extends AuditableEntity {

	/** "Amazon.in" <auto-confirm@amazon.in> */
	private static final Pattern NAME_AND_ADDRESS = Pattern.compile("^\\s*\"?([^\"<]*?)\"?\\s*<([^>]+)>\\s*$");

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_messages_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@Column(name = "gmail_message_id", nullable = false, length = 64)
	private String gmailMessageId;

	@Column(name = "from_name", length = 200)
	private String fromName;

	@Column(name = "from_email", nullable = false, length = 254)
	private String fromEmail;

	@Column(name = "subject", nullable = false, length = 500)
	private String subject;

	@Column(name = "snippet", nullable = false, length = 500)
	private String snippet;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	/** PDF and image attachments only: those are what can be imported. Stored as JSON. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "attachments", nullable = false)
	private List<GmailAttachment> attachments;

	@Enumerated(EnumType.STRING)
	@Column(name = "detected_type", length = 20)
	private DocumentType detectedType;

	/** 0..1: how likely the email holds a purchase document ({@link EmailClassifier}). */
	@Column(name = "confidence", nullable = false)
	private double confidence;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private GmailMessageStatus status = GmailMessageStatus.NEW;

	public GmailMessage(User user, GoogleApi.Email email, List<GmailAttachment> attachments,
			EmailClassifier.Verdict verdict) {
		this.user = user;
		this.gmailMessageId = email.id();
		Matcher from = NAME_AND_ADDRESS.matcher(email.from());
		this.fromName = from.matches() ? shorten(from.group(1), 200) : null;
		this.fromEmail = shorten(from.matches() ? from.group(2) : email.from(), 254);
		this.subject = shorten(email.subject(), 500);
		this.snippet = shorten(email.snippet(), 500);
		this.receivedAt = email.receivedAt();
		this.attachments = attachments;
		this.detectedType = verdict.type();
		this.confidence = verdict.confidence();
	}

	public void markImported() {
		status = GmailMessageStatus.IMPORTED;
	}

	/** Only a NEW email can be ignored; an imported one stays imported. */
	public void ignore() {
		if (status == GmailMessageStatus.NEW) {
			status = GmailMessageStatus.IGNORED;
		}
	}

	private static String shorten(String text, int max) {
		String value = text == null ? "" : text.strip();
		return value.length() > max ? value.substring(0, max) : value;
	}
}
