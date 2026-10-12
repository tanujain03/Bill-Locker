package project.bill_locker.gmail;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GmailConnectStateRepository extends JpaRepository<GmailConnectState, UUID> {

	Optional<GmailConnectState> findByStateHash(String stateHash);

	/** Housekeeping: links nobody came back through. */
	void deleteByUserIdAndExpiresAtBefore(UUID userId, Instant now);

	/** Returns 1 only for the caller that really deleted it, so two simultaneous callbacks cannot both win. */
	@Modifying
	@Query("delete from GmailConnectState s where s.stateHash = :hash")
	int consume(@Param("hash") String hash);

}
