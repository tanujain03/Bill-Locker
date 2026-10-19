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
@Table(name="bill_split_shares", uniqueConstraints=@UniqueConstraint(name="uk_split_share_member",columnNames={"expense_id","member_id"}))
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
class BillSplitShare extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="expense_id",nullable=false,foreignKey=@ForeignKey(name="fk_split_share_expense"))
 @OnDelete(action=OnDeleteAction.CASCADE) private BillSplitExpense expense;
 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="member_id",nullable=false,foreignKey=@ForeignKey(name="fk_split_share_member")) private BillSplitMember member;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
 BillSplitShare(BillSplitExpense expense,BillSplitMember member,BigDecimal amount) { this.expense=expense; this.member=member; this.amount=amount; }
}
