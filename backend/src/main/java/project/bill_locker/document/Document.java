package project.bill_locker.document;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.BaseEntity;
import project.bill_locker.user.User;

/**
 * One uploaded bill, stored in {@code documents}: the file's name/type/size plus the
 * details that belong to the whole bill (column names = the CSV's field names).
 * The products on the bill are {@link DocumentItem}s; the file's bytes are in
 * {@link DocumentFile}.
 */
@Entity
@Table(name = "documents", indexes = {
		@Index(name = "idx_documents_user", columnList = "user_id"),
		@Index(name = "idx_documents_read_queued", columnList = "read_queued_at")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_documents_user"))
	@OnDelete(action = OnDeleteAction.CASCADE) // deleting a user deletes their documents
	private User user;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 50)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private DocumentStatus status;

	// ---- Details of the whole bill (all optional: a bill may not show them) ----

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", length = 20)
	private DocumentType documentType;

	@Column(name = "document_number", length = 500)
	private String documentNumber;

	@Column(name = "seller_name", length = 500)
	private String sellerName;

	@Column(name = "seller_address", length = 500)
	private String sellerAddress;

	@Column(name = "seller_contact", length = 500)
	private String sellerContact;

	@Column(name = "buyer_name", length = 500)
	private String buyerName;

	@Column(name = "buyer_address", length = 500)
	private String buyerAddress;

	@Column(name = "buyer_email", length = 500)
	private String buyerEmail;

	@Column(name = "purchase_date")
	private LocalDate purchaseDate;

	@Column(name = "tax_amount", precision = 12, scale = 2)
	private BigDecimal taxAmount;

	@Column(name = "total_amount", precision = 12, scale = 2)
	private BigDecimal totalAmount;

	// ---- Background reading (see DocumentReadWorker) ----

	/** Set while the document waits for the AI worker; null once read, failed or edited by the user. */
	@Column(name = "read_queued_at")
	private Instant readQueuedAt;

	/** Why the last background read failed (shown so the user can retry by hand). */
	@Column(name = "read_error", length = 500)
	private String readError;

	/** The Gmail address this bill was imported from; null for manual uploads. */
	@Column(name = "source_gmail", length = 254)
	private String sourceGmail;

	/**
	 * The products on the bill, in the order they appear. orphanRemoval: an item
	 * taken out of this list is deleted from the table when the document is saved.
	 */
	@OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	private List<DocumentItem> items = new ArrayList<>();

	public Document(User user, String fileName, String contentType, long sizeBytes) {
		this(user, fileName, contentType, sizeBytes, null);
	}

	public Document(User user, String fileName, String contentType, long sizeBytes, String sourceGmail) {
		this.sourceGmail = sourceGmail;
		this.user = user;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.status = DocumentStatus.UPLOADED;
	}

	/**
	 * Replaces all details and products (used by "read with AI" and by "save").
	 * Old item rows are deleted because they leave the list (orphanRemoval).
	 */
	void replaceDetails(DocumentDetails details, DocumentStatus newStatus) {
		this.documentType = details.documentType();
		this.documentNumber = details.documentNumber();
		this.sellerName = details.sellerName();
		this.sellerAddress = details.sellerAddress();
		this.sellerContact = details.sellerContact();
		this.buyerName = details.buyerName();
		this.buyerAddress = details.buyerAddress();
		this.buyerEmail = details.buyerEmail();
		this.purchaseDate = details.purchaseDate();
		this.taxAmount = details.taxAmount();
		this.totalAmount = details.totalAmount();
		this.items.clear();
		for (DocumentItemView item : details.items()) {
			this.items.add(new DocumentItem(this, items.size(), item));
		}
		this.status = newStatus;
		// A manual read or save takes the document out of the reading queue.
		this.readQueuedAt = null;
		this.readError = null;
	}

	void queueForReading() {
		this.readQueuedAt = Instant.now();
		this.readError = null;
	}

	/** The document stays as it was (UPLOADED); only the reason is kept. */
	void readFailed(String message) {
		this.readQueuedAt = null;
		this.readError = message;
	}
}
