package project.bill_locker.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Database access for documents. Every query includes the owner's id. */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

	/** The document only if it belongs to this user (Spring Data builds the query from the name). */
	Optional<Document> findByIdAndUserId(UUID id, UUID userId);

	/** A user's documents, newest first. A null filter means "any". */
	@Query("""
			select d from Document d
			where d.user.id = :userId
			  and (:status is null or d.processingStatus = :status)
			  and (:documentType is null or d.documentType = :documentType)
			order by d.createdAt desc
			""")
	List<Document> findForUser(@Param("userId") UUID userId, @Param("status") ProcessingStatus status,
			@Param("documentType") DocumentType documentType);

	/** The next document waiting to be read: the oldest one with this status (for the background reader). */
	Optional<Document> findFirstByProcessingStatusOrderByCreatedAtAsc(ProcessingStatus status);

	List<Document> findByProcessingStatus(ProcessingStatus status);
}
