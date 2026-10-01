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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.product.Product;
import project.bill_locker.user.User;

/**
 * An uploaded bill, invoice, warranty card or receipt: the details shown in lists.
 * The file itself is stored separately, in {@link DocumentFile}.
 */
@Entity
@Table(name = "documents", indexes = {
		@Index(name = "idx_documents_user_created", columnList = "user_id, created_at DESC"),
		@Index(name = "idx_documents_product", columnList = "product_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends AuditableEntity {

	/** Who uploaded it. Deleting the user deletes their documents too (ON DELETE CASCADE). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_documents_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** The product this bill belongs to, once linked. Deleting the product keeps the bill (SET NULL). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_documents_product"))
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Product product;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 20)
	private DocumentType documentType;

	/** The original file name, cleaned so it can never be a path. */
	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	/** Worked out from the file's content, not taken from the browser. */
	@Column(name = "mime_type", nullable = false, length = 100)
	private String mimeType;

	@Column(name = "file_size", nullable = false)
	private long fileSize;

	/**
	 * UPLOADED → PROCESSING → PROCESSED (or FAILED): the background reader moves it along.
	 * CONFIRMED once the user saved the details as a product.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 20)
	private ProcessingStatus processingStatus = ProcessingStatus.UPLOADED;

	/** Which part of the reading is running (only while PROCESSING, or where a failure stopped). */
	@Enumerated(EnumType.STRING)
	@Column(name = "processing_stage", length = 12)
	private ProcessingStage processingStage;

	/** User-readable reason when the reading FAILED. */
	@Column(name = "error_message", length = 500)
	private String errorMessage;

	/** All the text found in the file (from the PDF itself, or by OCR). */
	@Column(name = "extracted_text", columnDefinition = "text")
	private String extractedText;

	/** The details found in that text, stored as JSON (jsonb): suggestions, not product data. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "extraction")
	private ExtractionResult extraction;

	public Document(User user, String fileName, String mimeType, long fileSize, DocumentType documentType) {
		this.user = user;
		this.fileName = fileName;
		this.mimeType = mimeType;
		this.fileSize = fileSize;
		this.documentType = documentType;
	}

	public void startProcessing() {
		processingStatus = ProcessingStatus.PROCESSING;
		processingStage = ProcessingStage.OCR;
		errorMessage = null;
	}

	public void moveToStage(ProcessingStage stage) {
		processingStage = stage;
	}

	public void finishProcessing(String text, ExtractionResult details) {
		extractedText = text;
		extraction = details;
		processingStatus = ProcessingStatus.PROCESSED;
		processingStage = null;
	}

	/** Keeps the stage, so the app can show where the reading stopped. */
	public void failProcessing(String reason) {
		processingStatus = ProcessingStatus.FAILED;
		errorMessage = reason;
	}

	/** E.g. "Add document" on a product's page: the bill belongs to that product. */
	public void attachTo(Product product) {
		this.product = product;
	}

	/** "Confirm & Save": the user checked the details and saved them as this product. */
	public void confirm(Product product, DocumentType documentType) {
		this.product = product;
		this.documentType = documentType;
		this.processingStatus = ProcessingStatus.CONFIRMED;
	}

	/** Back in the queue: the background reader will read it (again). */
	public void queueForProcessing() {
		processingStatus = ProcessingStatus.UPLOADED;
		processingStage = null;
		errorMessage = null;
		extractedText = null;
		extraction = null;
	}
}
