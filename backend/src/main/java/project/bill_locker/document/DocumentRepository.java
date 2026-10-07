package project.bill_locker.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	/** Finds a document only if it belongs to this user (anyone else's → empty → 404). */
	Optional<Document> findByIdAndUserId(UUID id, UUID userId);

	/** The document that has waited longest for the AI worker. */
	Optional<Document> findFirstByReadQueuedAtIsNotNullOrderByReadQueuedAtAsc();

	/**
	 * The user's documents, newest first. A null type/status means "any".
	 * {@code search} is a lower-case LIKE pattern such as "%croma%", or "" for no search;
	 * it is matched against the file name, document number, seller and product names.
	 * "distinct": a bill with two matching products must still appear once.
	 */
	@Query("""
			select distinct d from Document d left join d.items i
			where d.user.id = :userId
			  and (:type is null or d.documentType = :type)
			  and (:status is null or d.status = :status)
			  and (:search = ''
			       or lower(d.fileName) like :search
			       or lower(d.documentNumber) like :search
			       or lower(d.sellerName) like :search
			       or lower(i.productName) like :search)
			order by d.createdAt desc
			""")
	List<Document> search(@Param("userId") UUID userId, @Param("type") DocumentType type,
			@Param("status") DocumentStatus status, @Param("search") String search);

	/**
	 * The user's saved bills with their products, in one query ("join fetch"), for the
	 * dashboard and the warranties page. Only saved bills: their details were checked.
	 */
	@Query("""
			select distinct d from Document d left join fetch d.items
			where d.user.id = :userId and d.status = project.bill_locker.document.DocumentStatus.SAVED
			""")
	List<Document> findSavedWithItems(@Param("userId") UUID userId);

	// ---- Counts and lists for the dashboard ----

	long countByUserIdAndStatus(UUID userId, DocumentStatus status);

	/** Waiting for the background AI read. */
	long countByUserIdAndReadQueuedAtIsNotNull(UUID userId);

	/** The background AI read failed and nobody has read it since. */
	long countByUserIdAndStatusAndReadErrorIsNotNullAndReadQueuedAtIsNull(UUID userId, DocumentStatus status);

	List<Document> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);
}
