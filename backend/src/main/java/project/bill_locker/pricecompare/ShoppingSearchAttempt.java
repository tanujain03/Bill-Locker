package project.bill_locker.pricecompare;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import project.bill_locker.common.BaseEntity;
@Entity @Table(name="price_compare_attempts",indexes=@Index(name="idx_price_attempt_user_time",columnList="user_id,attempted_at"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
class ShoppingSearchAttempt extends BaseEntity {
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(name="attempted_at",nullable=false) private Instant attemptedAt;
 ShoppingSearchAttempt(UUID userId,Instant now) {this.userId=userId;attemptedAt=now;}
}
