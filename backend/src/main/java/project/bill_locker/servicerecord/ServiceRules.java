package project.bill_locker.servicerecord;

import java.time.LocalDate;

/** Date rules take today explicitly; callers obtain it from the application's Clock. */
public final class ServiceRules {
 private ServiceRules() {}
 public static ServiceDueStatus status(LocalDate due, LocalDate today) {
  if (due == null) return ServiceDueStatus.NONE;
  if (due.isBefore(today)) return ServiceDueStatus.OVERDUE;
  return due.isAfter(today.plusDays(30)) ? ServiceDueStatus.UPCOMING : ServiceDueStatus.DUE_SOON;
 }
 public static LocalDate nextDue(LocalDate date, LocalDate explicit, Integer months) {
  return months == null ? explicit : date.plusMonths(months);
 }
}
