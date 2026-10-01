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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.user.User;

/**
 * An uploaded bill, invoice, warranty card or receipt: the details shown in lists.
 * The file itself is stored separately, in {@link DocumentFile}.
 */
@Entity
@Table(name = "documents", indexes = @Index(name = "idx_documents_user_created", columnList = "user_id, created_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends AuditableEntity {

	/** Who uploaded it. Deleting the user deletes their documents too (ON DELETE CASCADE). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_documents_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

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

	/** Always UPLOADED for now; reading the document (OCR) is the next step. */
	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 20)
	private ProcessingStatus processingStatus = ProcessingStatus.UPLOADED;

	public Document(User user, String fileName, String mimeType, long fileSize, DocumentType documentType) {
		this.user = user;
		this.fileName = fileName;
		this.mimeType = mimeType;
		this.fileSize = fileSize;
		this.documentType = documentType;
	}
}
