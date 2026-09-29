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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentSource;
import project.bill_locker.document.DocumentType;
import project.bill_locker.user.User;

/**
 * An email the Gmail scan shortlisted as a likely bill. Metadata only — email
 * bodies are never stored. Unique per (user, Gmail message id), so re-scanning
 * never creates duplicates.
 */
@Entity
@Table(name = "gmail_messages",
		uniqueConstraints = @UniqueConstraint(name = "uk_gmail_messages_external", columnNames = {"user_id", "gmail_message_id"}),
		indexes = @Index(name = "idx_gmail_messages_user_status", columnList = "user_id, status, received_at DESC"))
@Getter
@Setter
@NoArgsConstructor
public class GmailMessage extends AuditableEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_messages_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@NotBlank
	@Size(max = 64)
	@Column(name = "gmail_message_id", nullable = false, length = 64)
	private String gmailMessageId;

	@Size(max = 64)
	@Column(name = "gmail_thread_id", length = 64)
	private String gmailThreadId;

	@Size(max = 200)
	@Column(name = "from_name", length = 200)
	private String fromName;

	@NotBlank
	@Size(max = 254)
	@Column(name = "from_email", nullable = false, length = 254)
	private String fromEmail;

	@Size(max = 500)
	@Column(name = "subject", nullable = false, length = 500)
	private String subject = "";

	@Size(max = 500)
	@Column(name = "snippet", nullable = false, length = 500)
	private String snippet = "";

	@NotNull
	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	/** Attachment metadata (stored as jsonb); empty when the bill is in the email body. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "attachments")
	private List<GmailAttachment> attachments = new ArrayList<>();

	/** What the AI thinks the email contains (null if not a purchase document). */
	@Enumerated(EnumType.STRING)
	@Column(name = "detected_type", length = 20)
	private DocumentType detectedType;

	/** 0..1 — how likely the email contains a purchase document. */
	@NotNull
	@DecimalMin("0")
	@DecimalMax("1")
	@Column(name = "confidence", nullable = false, precision = 4, scale = 3)
	private BigDecimal confidence = BigDecimal.ZERO;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private GmailMessageStatus status = GmailMessageStatus.NEW;

	/** Documents created when this email was imported (one per attachment). Not cascaded. */
	@OneToMany(mappedBy = "gmailMessage")
	private List<Document> documents = new ArrayList<>();

	public GmailMessage(User user, String gmailMessageId, String fromEmail, Instant receivedAt) {
		this.user = user;
		this.gmailMessageId = gmailMessageId;
		this.fromEmail = fromEmail;
		this.receivedAt = receivedAt;
	}

	/** Records a document imported from this email (both sides of the relation). */
	public void addImportedDocument(Document document) {
		if (!documents.contains(document)) {
			documents.add(document);
		}
		document.setGmailMessage(this);
		document.setSource(DocumentSource.GMAIL);
		status = GmailMessageStatus.IMPORTED;
	}

	/** Disconnecting Gmail deletes the shortlist; imported documents stay in the locker. */
	@PreRemove
	void unlinkDocuments() {
		documents.forEach(document -> document.setGmailMessage(null));
		documents.clear();
	}
}
