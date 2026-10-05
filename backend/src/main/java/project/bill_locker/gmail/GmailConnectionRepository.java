package project.bill_locker.gmail;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, UUID> {

	Optional<GmailConnection> findByUserId(UUID userId);

	List<GmailConnection> findBySyncStatus(GmailSyncStatus syncStatus);

	List<GmailConnection> findByAutoSyncTrue();
}
