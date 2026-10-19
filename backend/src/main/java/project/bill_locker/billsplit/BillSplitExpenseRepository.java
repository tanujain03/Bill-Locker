package project.bill_locker.billsplit;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface BillSplitExpenseRepository extends JpaRepository<BillSplitExpense,UUID> {
List<BillSplitExpense> findByGroupIdOrderByCreatedAtDescIdDesc(UUID groupId);
 Optional<BillSplitExpense> findByGroupIdAndRequestId(UUID groupId,UUID requestId);
}
