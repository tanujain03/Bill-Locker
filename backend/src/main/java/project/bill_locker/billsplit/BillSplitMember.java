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
@Table(name="bill_split_members", indexes=@Index(name="idx_split_members_user",columnList="user_id"), uniqueConstraints={@UniqueConstraint(name="uk_split_member_user",columnNames={"group_id","user_id"}),@UniqueConstraint(name="uk_split_member_name",columnNames={"group_id","name_key"})})
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
class BillSplitMember extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY, optional=false)
 @JoinColumn(name="group_id", nullable=false, foreignKey=@ForeignKey(name="fk_split_members_group"))
 @OnDelete(action=OnDeleteAction.CASCADE) private BillSplitGroup group;
 @Column(name="user_id") private UUID userId;
 @Column(nullable=false,length=80) private String name;
 @Column(name="name_key",nullable=false,length=240) private String nameKey;
 BillSplitMember(BillSplitGroup group,String name,UUID userId) { this.group=group; this.name=name.strip(); this.nameKey=this.name.toLowerCase(Locale.ROOT); this.userId=userId; }
 void claim(UUID userId) { this.userId=userId; }
}
