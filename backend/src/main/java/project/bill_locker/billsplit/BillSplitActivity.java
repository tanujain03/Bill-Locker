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
@Table(name="bill_split_activity",indexes=@Index(name="idx_split_activity_group",columnList="group_id,created_at"))
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
class BillSplitActivity extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY, optional=false)
 @JoinColumn(name="group_id", nullable=false, foreignKey=@ForeignKey(name="fk_split_activity_group"))
 @OnDelete(action=OnDeleteAction.CASCADE) private BillSplitGroup group;
 @Column(name="actor_id",nullable=false) private UUID actorId;
 @Column(nullable=false,length=500) private String message;
 BillSplitActivity(BillSplitGroup group,UUID actor,String message) { this.group=group; actorId=actor; this.message=message; }
}
