package project.bill_locker.billsplit;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.BaseEntity;

@Entity
@Table(name="bill_split_groups")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
class BillSplitGroup extends BaseEntity {
 @Column(name="owner_id", nullable=false) private UUID ownerId;
 @Column(nullable=false, length=100) private String name;
 BillSplitGroup(UUID ownerId,String name) { this.ownerId=ownerId; this.name=name; }
}
