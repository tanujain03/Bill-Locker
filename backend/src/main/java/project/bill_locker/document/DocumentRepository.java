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
}
