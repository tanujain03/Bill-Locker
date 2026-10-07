package project.bill_locker.gmail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.BaseEntity;
import project.bill_locker.document.Document;

/** An attachment of a found email, stored in {@code gmail_files}. Downloaded only when imported. */
@Entity
@Table(name = "gmail_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GmailFile extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "email_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_files_email"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private GmailEmail email;

	/** The attachment's MIME part id: stable across fetches, unlike Gmail's attachment ids. */
	@Column(name = "part_id", nullable = false, length = 32)
	private String partId;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private GmailFileStatus status;

	/** Set once imported; if the user deletes the document the file stays, with no document. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "document_id", foreignKey = @ForeignKey(name = "fk_gmail_files_document"))
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Document document;

	@Column(name = "error", length = 500)
	private String error;

	public GmailFile(GmailEmail email, String partId, String fileName, String contentType, long sizeBytes) {
		this.email = email;
		this.partId = partId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.status = GmailFileStatus.NEW;
	}

	/** Import (again): clears an old failure. */
	public void queueImport() {
		this.status = GmailFileStatus.IMPORTING;
		this.error = null;
	}

	public void ignore() {
		this.status = GmailFileStatus.IGNORED;
	}

	public void restore() {
		this.status = GmailFileStatus.NEW;
	}

	public void finishImport(Document document) {
		this.status = GmailFileStatus.IMPORTED;
		this.document = document;
		this.error = null;
	}

	/** The reason is words the user can act on. */
	public void failImport(String reason) {
		this.status = GmailFileStatus.FAILED;
		this.error = reason;
	}
}
