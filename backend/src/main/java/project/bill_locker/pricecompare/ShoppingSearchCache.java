package project.bill_locker.pricecompare;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import project.bill_locker.common.BaseEntity;
@Entity @Table(name="price_compare_cache",indexes=@Index(name="idx_price_cache_user_key",columnList="user_id,query_key"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
class ShoppingSearchCache extends BaseEntity {
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(name="query_key",nullable=false,length=64) private String queryKey;
 @Column(name="checked_at",nullable=false) private Instant checkedAt;
 @Column(name="response_json",nullable=false,columnDefinition="text") private String responseJson;
 void updateResponse(String json) {responseJson=json;}
 ShoppingSearchCache(UUID user,String key,Instant now,String json) {userId=user;queryKey=key;checkedAt=now;responseJson=json;}
}
