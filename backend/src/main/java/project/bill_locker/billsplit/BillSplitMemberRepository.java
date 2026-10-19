package project.bill_locker.billsplit;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface BillSplitMemberRepository extends JpaRepository<BillSplitMember,UUID> {
List<BillSplitMember> findByGroupIdOrderByCreatedAtAscIdAsc(UUID groupId);
}
