package project.bill_locker.servicerecord;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
class ServiceRulesTests {
 private final LocalDate today=LocalDate.of(2026,10,7);
 @Test void statusBoundaries() {
  assertEquals(ServiceDueStatus.NONE,ServiceRules.status(null,today));
  assertEquals(ServiceDueStatus.OVERDUE,ServiceRules.status(today.minusDays(1),today));
  assertEquals(ServiceDueStatus.DUE_SOON,ServiceRules.status(today,today));
  assertEquals(ServiceDueStatus.DUE_SOON,ServiceRules.status(today.plusDays(30),today));
  assertEquals(ServiceDueStatus.UPCOMING,ServiceRules.status(today.plusDays(31),today));
 }
 @Test void repeatMonthsClampToTheLastDayAndTakePrecedence() {
  assertEquals(LocalDate.of(2027,2,28),ServiceRules.nextDue(LocalDate.of(2027,1,31),null,1));
  assertEquals(LocalDate.of(2025,2,28),ServiceRules.nextDue(LocalDate.of(2024,2,29),today,12));
  assertEquals(today,ServiceRules.nextDue(today.minusDays(1),today,null));
 }
}
