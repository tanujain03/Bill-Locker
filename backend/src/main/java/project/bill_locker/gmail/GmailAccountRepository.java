package project.bill_locker.gmail;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailAccountRepository extends JpaRepository<GmailAccount, UUID> {

	List<GmailAccount> findByUserIdOrderByCreatedAtAsc(UUID userId);

	Optional<GmailAccount> findByIdAndUserId(UUID id, UUID userId);

	Optional<GmailAccount> findByUserIdAndEmail(UUID userId, String email);

	/** The scan that has waited longest. */
	Optional<GmailAccount> findFirstByScanStatusOrderByUpdatedAtAsc(GmailScanStatus status);

	List<GmailAccount> findByScanStatus(GmailScanStatus status);
}
