package project.bill_locker.billsplit;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface BillSplitSettlementRepository extends JpaRepository<BillSplitSettlement,UUID> {
List<BillSplitSettlement> findByGroupIdOrderByCreatedAtDescIdDesc(UUID groupId);
 Optional<BillSplitSettlement> findByGroupIdAndRequestId(UUID groupId,UUID requestId);
}
