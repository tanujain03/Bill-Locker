package project.bill_locker.pricecompare;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface ShoppingSearchCacheRepository extends JpaRepository<ShoppingSearchCache,UUID> {
 Optional<ShoppingSearchCache> findFirstByUserIdAndQueryKeyOrderByCheckedAtDesc(UUID user,String key);
}
