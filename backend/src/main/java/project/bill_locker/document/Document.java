package project.bill_locker.document;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.gmail.GmailMessage;
import project.bill_locker.product.Product;
import project.bill_locker.user.User;

/**
 * A bill, invoice, warranty card or receipt. The file bytes live in object
 * storage (MinIO/S3) under {@link #storageKey}; this row holds metadata, the
 * OCR text and the AI extraction waiting for review.
 */
@Entity
@Table(name = "documents",
		uniqueConstraints = @UniqueConstraint(name = "uk_documents_storage_key", columnNames = "storage_key"),
		indexes = {
				@Index(name = "idx_documents_user_created", columnList = "user_id, created_at DESC"),
				@Index(name = "idx_documents_user_status", columnList = "user_id, processing_status"),
				@Index(name = "idx_documents_product", columnList = "product_id"),
				@Index(name = "idx_documents_user_hash", columnList = "user_id, content_hash"),
				@Index(name = "idx_documents_gmail_message", columnList = "gmail_message_id")
		})
@Getter
@Setter
@NoArgsConstructor
public class Document extends AuditableEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_documents_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** Set when the user confirms (or uploads from a product page). Deleting the product sets this to NULL. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_documents_product"))
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Product product;

	/** The email this document was imported from (Gmail import only). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "gmail_message_id", foreignKey = @ForeignKey(name = "fk_documents_gmail_message"))
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private GmailMessage gmailMessage;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 20)
	private DocumentType documentType = DocumentType.OTHER;

	/** Original name, sanitised — never a path. */
	@NotBlank
	@Size(max = 255)
	@Pattern(regexp = "[^/\\\\]+")
	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@NotBlank
	@Size(max = 512)
	@Column(name = "storage_key", nullable = false, length = 512)
	private String storageKey;

	@NotBlank
	@Pattern(regexp = "application/pdf|image/jpeg|image/png|image/webp")
	@Column(name = "mime_type", nullable = false, length = 100)
	private String mimeType;

	@Positive
	@Column(name = "file_size", nullable = false)
	private long fileSize;

	/** SHA-256 (hex) of the file, to spot duplicate uploads. */
	@Pattern(regexp = "[0-9a-f]{64}")
	@Column(name = "content_hash", length = 64)
	private String contentHash;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 10)
	private DocumentSource source = DocumentSource.UPLOAD;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 20)
	private ProcessingStatus processingStatus = ProcessingStatus.UPLOADED;

	/** Only meaningful while PROCESSING (or where a FAILED run stopped). */
	@Enumerated(EnumType.STRING)
	@Column(name = "processing_stage", length = 12)
	private ProcessingStage processingStage;

	@Column(name = "processing_attempts", nullable = false)
	private int processingAttempts;

	/** User-readable reason when processing FAILED. */
	@Size(max = 500)
	@Column(name = "error_message", length = 500)
	private String errorMessage;

	/** OCR output: shown on the review screen and chunked for RAG. */
	@Column(name = "extracted_text", columnDefinition = "text")
	private String extractedText;

	/** AI extraction (stored as jsonb) — a suggestion until the user confirms. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "extraction")
	private ExtractionResult extraction;

	@Size(max = 100)
	@Column(name = "ai_model", length = 100)
	private String aiModel;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	public Document(User user, String fileName, String storageKey, String mimeType, long fileSize) {
		this.user = user;
		this.fileName = fileName;
		this.storageKey = storageKey;
		this.mimeType = mimeType;
		this.fileSize = fileSize;
	}

	public void startProcessing(ProcessingStage stage) {
		processingStatus = ProcessingStatus.PROCESSING;
		processingStage = stage;
		errorMessage = null;
	}

	public void markReadyForReview(ExtractionResult result, String extractedText, String aiModel) {
		this.extraction = result;
		this.extractedText = extractedText;
		this.aiModel = aiModel;
		this.processingStatus = ProcessingStatus.REVIEW_REQUIRED;
		this.processingStage = null;
		this.processedAt = Instant.now();
	}

	public void markFailed(String reason) {
		processingStatus = ProcessingStatus.FAILED;
		errorMessage = reason;
	}

	/** The user reviewed the extraction and saved it to {@code product}. */
	public void confirm(Product product, DocumentType type) {
		product.attachDocument(this);
		this.documentType = type;
		this.processingStatus = ProcessingStatus.CONFIRMED;
		this.processingStage = null;
		this.confirmedAt = Instant.now();
	}
}
