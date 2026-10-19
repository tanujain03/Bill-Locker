package project.bill_locker.billsplit;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class SplitViews {
 private SplitViews() {}
 public record GroupSummary(UUID id,String name,boolean owner,int members) {}
 public record Member(UUID id,String name,boolean joined,boolean self,boolean owner,BigDecimal balance) {}
 public record Share(UUID memberId,BigDecimal amount) {}
 public record Expense(UUID id,String title,BigDecimal amount,LocalDate expenseDate,UUID payerId,SplitMode mode,
  List<Share> shares,boolean voided,String sellerName,String documentNumber) {}
 public record Settlement(UUID id,UUID fromMemberId,UUID toMemberId,BigDecimal amount,SettlementStatus status,
  boolean canConfirm,boolean canReject,Instant createdAt) {}
 public record Activity(UUID id,String message,Instant createdAt) {}
 public record GroupDetail(UUID id,String name,boolean owner,List<Member> members,List<Expense> expenses,
  List<Settlement> settlements,List<Activity> activity) {}
 public record Invitation(String token,Instant expiresAt,String memberName) {}
 public record Preview(String groupName,String memberName,Instant expiresAt) {}
}
