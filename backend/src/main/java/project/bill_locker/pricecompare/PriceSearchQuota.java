package project.bill_locker.pricecompare;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
/** Rolling 31-day budget for this installation. Reservations survive failures, restarts and concurrent app instances. */
@Service
class PriceSearchQuota {
 private final ShoppingSearchAttemptRepository attempts;
 private final JdbcTemplate jdbc;
 private final Clock clock;
 private final int limit;
 PriceSearchQuota(ShoppingSearchAttemptRepository attempts,JdbcTemplate jdbc,Clock clock,
  @Value("${app.price-compare.request-limit:200}") int limit) {
  this.attempts=attempts;this.jdbc=jdbc;this.clock=clock;this.limit=Math.clamp(limit,0,250);
 }
 @Transactional
 public void reserve(UUID user) {
  // PostgreSQL transaction lock serializes count + insert across all backend instances.
  jdbc.execute("select pg_advisory_xact_lock(1700170017)");
  if(attempts.countByAttemptedAtGreaterThanEqual(clock.instant().minus(Duration.ofDays(31)))>=limit) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"PRICE_SEARCH_LIMIT","The app's free search budget has been reached. Recent cached searches are still available.");
  if(attempts.countByAttemptedAtGreaterThanEqual(clock.instant().minus(Duration.ofHours(1)))>=40)
   throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"PRICE_HOURLY_LIMIT","Price searches are temporarily limited. Try a recent search or return later.");
  Instant day=LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
  if(attempts.countByUserIdAndAttemptedAtGreaterThanEqual(user,day)>=5)
   throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"PRICE_DAILY_LIMIT","You have used today's five price searches or store lookups. Try a recent result or return tomorrow.");
  attempts.saveAndFlush(new ShoppingSearchAttempt(user,clock.instant()));
 }
 public int remaining() {return Math.max(0,limit-(int)attempts.countByAttemptedAtGreaterThanEqual(clock.instant().minus(Duration.ofDays(31))));}
 public int limit() {return limit;}
}
