package project.bill_locker.gmail;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GmailFileRepository extends JpaRepository<GmailFile, UUID> {

	/** How many of the user's files are in any of these statuses (the tabs' numbers). */
	@Query("select count(f) from GmailFile f where f.email.user.id = :userId and f.status in :statuses")
	long countByUserAndStatusIn(@Param("userId") UUID userId, @Param("statuses") Collection<GmailFileStatus> statuses);

	/** The user's files in these statuses, newest email first (emails and accounts loaded with them). */
	@Query("""
			select f from GmailFile f join fetch f.email e join fetch e.account
			where e.user.id = :userId and f.status in :statuses
			order by e.receivedAt desc, f.fileName asc""")
	List<GmailFile> findForView(@Param("userId") UUID userId, @Param("statuses") Collection<GmailFileStatus> statuses);

	/** Only the user's own: an id that is someone else's is simply not returned. */
	@Query("select f from GmailFile f where f.id in :ids and f.email.user.id = :userId")
	List<GmailFile> findOwned(@Param("ids") Collection<UUID> ids, @Param("userId") UUID userId);

	/** Files waiting for the import worker, oldest first. */
	@Query("""
			select f from GmailFile f join fetch f.email e join fetch e.account
			where f.status = :status order by f.createdAt asc""")
	List<GmailFile> findByStatusOldestFirst(@Param("status") GmailFileStatus status, Pageable page);
}
