package project.bill_locker.billsplit;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface BillSplitInvitationRepository extends JpaRepository<BillSplitInvitation,UUID> {
Optional<BillSplitInvitation> findByMemberId(UUID memberId);
 Optional<BillSplitInvitation> findByTokenHash(String hash);
 @org.springframework.data.jpa.repository.Query("select i.group.id from BillSplitInvitation i where i.tokenHash=:hash")
 Optional<UUID> groupForToken(String hash);
}
