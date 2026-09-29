package project.bill_locker.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import project.bill_locker.common.BaseEntity;
import project.bill_locker.document.Document;
import project.bill_locker.user.User;

/**
 * A piece of a document's text with its embedding, for RAG (pgvector).
 * The owner is copied from the document and cannot be changed, and every
 * similarity search must filter on {@code user} so one user's chunks are
 * never retrieved for another.
 */
@Entity
@Table(name = "document_chunks",
		uniqueConstraints = @UniqueConstraint(name = "uk_document_chunks_position", columnNames = {"document_id", "chunk_index"}),
		indexes = {
				@Index(name = "idx_document_chunks_user", columnList = "user_id"),
				@Index(name = "idx_document_chunks_document", columnList = "document_id")
		})
@Getter
@NoArgsConstructor
public class DocumentChunk extends BaseEntity {

	/** Gemini embeddings requested with outputDimensionality = 768. */
	public static final int EMBEDDING_DIMENSIONS = 768;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_document_chunks_document"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Document document;

	/** Denormalised owner (spec: every vector query filters by user). */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_document_chunks_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@Column(name = "chunk_index", nullable = false)
	private int chunkIndex;

	@NotBlank
	@Column(name = "chunk_text", nullable = false, columnDefinition = "text")
	private String chunkText;

	@NotNull
	@JdbcTypeCode(SqlTypes.VECTOR)
	@Array(length = EMBEDDING_DIMENSIONS)
	@Column(name = "embedding", nullable = false)
	private float[] embedding;

	@NotBlank
	@Size(max = 100)
	@Column(name = "embedding_model", nullable = false, length = 100)
	private String embeddingModel;

	@Setter
	@Column(name = "page_number")
	private Integer pageNumber;

	/** Extra info such as section title or character offsets. */
	@Setter
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata")
	private Map<String, Object> metadata = new HashMap<>();

	public DocumentChunk(Document document, int chunkIndex, String chunkText, float[] embedding, String embeddingModel) {
		if (embedding == null || embedding.length != EMBEDDING_DIMENSIONS) {
			throw new IllegalArgumentException("Embedding must have " + EMBEDDING_DIMENSIONS + " dimensions");
		}
		this.document = document;
		this.user = document.getUser();
		this.chunkIndex = chunkIndex;
		this.chunkText = chunkText;
		this.embedding = embedding;
		this.embeddingModel = embeddingModel;
	}
}
