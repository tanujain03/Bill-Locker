package project.bill_locker.billsplit;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface BillSplitActivityRepository extends JpaRepository<BillSplitActivity,UUID> {
List<BillSplitActivity> findTop100ByGroupIdOrderByCreatedAtDescIdDesc(UUID groupId);
}
