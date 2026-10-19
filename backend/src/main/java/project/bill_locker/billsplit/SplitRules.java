package project.bill_locker.billsplit;
import java.math.BigDecimal;
import java.util.*;
/** Money uses exact paise. Equal-split remainders go to sorted member IDs deterministically. */
public final class SplitRules {
 private SplitRules() {}
 public static Map<UUID,BigDecimal> equal(BigDecimal total,Collection<UUID> ids) {
  List<UUID> members=ids.stream().sorted().toList();
  if(members.isEmpty() || new HashSet<>(members).size()!=members.size()) throw new IllegalArgumentException("Choose unique members");
  long paise=total.movePointRight(2).longValueExact();
  if(paise<=0) throw new IllegalArgumentException("Amount must be positive");
  Map<UUID,BigDecimal> result=new LinkedHashMap<>();
  for(int i=0;i<members.size();i++) result.put(members.get(i),BigDecimal.valueOf(paise/members.size()+(i<paise%members.size()?1:0),2));
  return result;
 }
}
