package project.bill_locker.gmail;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The shortlist of emails. Every query includes the owner's id. */
public interface GmailMessageRepository extends JpaRepository<GmailMessage, UUID> {

	List<GmailMessage> findByUserIdOrderByReceivedAtDesc(UUID userId);

	Optional<GmailMessage> findByIdAndUserId(UUID id, UUID userId);

	List<GmailMessage> findByIdInAndUserId(Collection<UUID> ids, UUID userId);

	boolean existsByUserIdAndGmailMessageId(UUID userId, String gmailMessageId);

	/** The Gmail ids already on the shortlist, so a scan only fetches emails it hasn't seen. */
	@Query("select m.gmailMessageId from GmailMessage m where m.user.id = :userId")
	Set<String> findGmailIds(@Param("userId") UUID userId);

	void deleteByUserId(UUID userId);
}
