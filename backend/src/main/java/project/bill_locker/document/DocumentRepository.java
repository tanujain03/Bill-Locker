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

	/**
	 * A user's documents, newest first. A null filter means "any". "join fetch" loads the
	 * linked product in the same query, for the product name shown in lists.
	 */
	@Query("""
			select d from Document d
			left join fetch d.product
			where d.user.id = :userId
			  and (:productId is null or d.product.id = :productId)
			  and (:status is null or d.processingStatus = :status)
			  and (:documentType is null or d.documentType = :documentType)
			order by d.createdAt desc
			""")
	List<Document> findForUser(@Param("userId") UUID userId, @Param("productId") UUID productId,
			@Param("status") ProcessingStatus status, @Param("documentType") DocumentType documentType);

	long countByProductId(UUID productId);

	/** How many documents each of the user's products has: one row per product. */
	@Query("""
			select d.product.id as productId, count(d) as documents from Document d
			where d.user.id = :userId and d.product is not null
			group by d.product.id
			""")
	List<ProductDocumentCount> countPerProduct(@Param("userId") UUID userId);

	/** One row of {@link #countPerProduct}: Spring Data fills it from the query's "as" names. */
	interface ProductDocumentCount {

		UUID getProductId();

		long getDocuments();
	}

	/** The next document waiting to be read: the oldest one with this status (for the background reader). */
	Optional<Document> findFirstByProcessingStatusOrderByCreatedAtAsc(ProcessingStatus status);

	List<Document> findByProcessingStatus(ProcessingStatus status);
}
