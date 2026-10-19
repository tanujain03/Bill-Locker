package project.bill_locker.billsplit;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface BillSplitGroupRepository extends JpaRepository<BillSplitGroup,UUID> {
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select g from BillSplitGroup g where g.id=:id")
 Optional<BillSplitGroup> lock(UUID id);
 @org.springframework.data.jpa.repository.Query("select m.group from BillSplitMember m where m.userId=:userId order by m.group.createdAt desc")
 List<BillSplitGroup> forUser(UUID userId);
}
