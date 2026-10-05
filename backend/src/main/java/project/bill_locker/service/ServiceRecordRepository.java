package project.bill_locker.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Database access for service records. They belong to a user through their product. */
public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, UUID> {

	/** The record only if its product belongs to this user. */
	Optional<ServiceRecord> findByIdAndProductUserId(UUID id, UUID userId);

	/** A user's records (of one product, or all when {@code productId} is null), newest service first. */
	@Query("""
			select r from ServiceRecord r
			join fetch r.product p
			where p.user.id = :userId
			  and (:productId is null or p.id = :productId)
			order by r.serviceDate desc, r.createdAt desc
			""")
	List<ServiceRecord> findForUser(@Param("userId") UUID userId, @Param("productId") UUID productId);

	/** A product's most recent record: its next-service date is the one that counts. */
	Optional<ServiceRecord> findFirstByProductIdOrderByServiceDateDescCreatedAtDesc(UUID productId);

	/** Each of the user's products with its most recent record. */
	default Map<UUID, ServiceRecord> latestPerProduct(UUID userId) {
		Map<UUID, ServiceRecord> latest = new LinkedHashMap<>();
		// The list is newest first, so the first record seen for a product is its latest.
		findForUser(userId, null).forEach(record -> latest.putIfAbsent(record.getProduct().getId(), record));
		return latest;
	}
}
