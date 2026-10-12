package project.bill_locker.gmail;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GmailEmailRepository extends JpaRepository<GmailEmail, UUID> {

	/** Message ids already stored for an account, so a rescan skips them. */
	@Query("select e.gmailMessageId from GmailEmail e where e.account.id = :accountId")
	Collection<String> findMessageIdsByAccount(@Param("accountId") UUID accountId);
}
