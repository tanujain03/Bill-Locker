package project.bill_locker.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Product lookups always include the owner, including registration and claim-kit requests. */
public interface DocumentItemRepository extends JpaRepository<DocumentItem, UUID> {
	Optional<DocumentItem> findByIdAndDocumentUserId(UUID id, UUID userId);
}
