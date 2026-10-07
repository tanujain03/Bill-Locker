package project.bill_locker.document;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The file bytes; the id is the document's id. */
public interface DocumentFileRepository extends JpaRepository<DocumentFile, UUID> {
}
