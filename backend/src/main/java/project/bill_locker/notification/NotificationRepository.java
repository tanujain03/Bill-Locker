package project.bill_locker.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Database access for notifications. Spring Data builds most queries from the method names. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByUserIdOrderByScheduledAtDesc(UUID userId);

	Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

	long countByUserIdAndReadFalse(UUID userId);

	boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

	/** One UPDATE statement for all of them, instead of loading and saving each one. */
	@Modifying
	@Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
	int markAllRead(@Param("userId") UUID userId);
}
