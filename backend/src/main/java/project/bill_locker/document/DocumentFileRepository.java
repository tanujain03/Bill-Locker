package project.bill_locker.document;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes file bytes. The id is the document's id. */
public interface DocumentFileRepository extends JpaRepository<DocumentFile, UUID> {
}
