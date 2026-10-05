package project.bill_locker.gmail;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

/** Sign-ins in progress, looked up by their state (the id). */
public interface GmailOAuthStateRepository extends JpaRepository<GmailOAuthState, String> {

	/** Sign-ins that were started but never finished. */
	void deleteByExpiresAtBefore(Instant moment);
}
