package project.bill_locker.pricecompare;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface ShoppingSearchAttemptRepository extends JpaRepository<ShoppingSearchAttempt,UUID> {
 long countByAttemptedAtGreaterThanEqual(Instant since);
 long countByUserIdAndAttemptedAtGreaterThanEqual(UUID userId,Instant since);
}
