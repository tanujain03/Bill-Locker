package project.bill_locker.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * The bytes of an uploaded file, stored in PostgreSQL (column type {@code bytea}).
 *
 * <p>It has its own table so that listing documents never loads whole files: the
 * bytes are read only when someone previews or downloads that one document.
 */
@Entity
@Table(name = "document_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentFile {

	/** The same id as the document (a shared primary key, filled in by {@code @MapsId}). */
	@Id
	private UUID documentId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", foreignKey = @ForeignKey(name = "fk_document_files_document"))
	@OnDelete(action = OnDeleteAction.CASCADE) // deleting a document deletes its file
	private Document document;

	@Column(name = "data", nullable = false)
	private byte[] data;

	public DocumentFile(Document document, byte[] data) {
		this.document = document;
		this.data = data;
	}
}
