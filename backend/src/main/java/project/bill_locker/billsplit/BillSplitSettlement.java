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
@Table(name="bill_split_settlements", uniqueConstraints=@UniqueConstraint(name="uk_split_settlement_request",columnNames={"group_id","request_id"}))
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
class BillSplitSettlement extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY, optional=false)
 @JoinColumn(name="group_id", nullable=false, foreignKey=@ForeignKey(name="fk_split_settlements_group"))
 @OnDelete(action=OnDeleteAction.CASCADE) private BillSplitGroup group;
 @Column(name="request_id",nullable=false) private UUID requestId;
 @Column(name="reported_by",nullable=false) private UUID reportedBy;
 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="from_member_id",nullable=false,foreignKey=@ForeignKey(name="fk_split_settlement_from")) private BillSplitMember fromMember;
 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="to_member_id",nullable=false,foreignKey=@ForeignKey(name="fk_split_settlement_to")) private BillSplitMember toMember;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private SettlementStatus status;
 @Column(name="decided_at") private Instant decidedAt;
 BillSplitSettlement(BillSplitGroup group,UUID user,SplitRequests.Settlement r,BillSplitMember from,BillSplitMember to) {
  this.group=group; requestId=r.requestId(); reportedBy=user; fromMember=from; toMember=to;
  amount=r.amount().setScale(2); status=SettlementStatus.PENDING;
 }
 void decide(SettlementStatus status,Instant now) { this.status=status; decidedAt=now; }
}
