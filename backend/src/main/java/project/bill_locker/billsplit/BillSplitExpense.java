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
@Table(name="bill_split_expenses", uniqueConstraints=@UniqueConstraint(name="uk_split_expense_request",columnNames={"group_id","request_id"}))
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
class BillSplitExpense extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY, optional=false)
 @JoinColumn(name="group_id", nullable=false, foreignKey=@ForeignKey(name="fk_split_expenses_group"))
 @OnDelete(action=OnDeleteAction.CASCADE) private BillSplitGroup group;
 @Column(name="request_id",nullable=false) private UUID requestId;
 @Column(nullable=false,length=200) private String title;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
 @Column(name="expense_date",nullable=false) private LocalDate expenseDate;
 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="payer_id",nullable=false,foreignKey=@ForeignKey(name="fk_split_expense_payer")) private BillSplitMember payer;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private SplitMode mode;
 @Column(name="source_document_id") private UUID sourceDocumentId;
 @Column(name="seller_name",length=500) private String sellerName;
 @Column(name="document_number",length=500) private String documentNumber;
 @Column(nullable=false) private boolean voided;
 @OneToMany(mappedBy="expense",cascade=CascadeType.ALL) @OrderBy("id") private List<BillSplitShare> shares=new ArrayList<>();
 BillSplitExpense(BillSplitGroup group,SplitRequests.Expense r,BillSplitMember payer) {
  this.group=group; requestId=r.requestId(); title=r.title().strip(); amount=r.amount().setScale(2);
  expenseDate=r.expenseDate(); mode=r.mode(); this.payer=payer; sourceDocumentId=r.sourceDocumentId();
 }
 void snapshot(String seller,String number) { sellerName=seller; documentNumber=number; }
 void add(BillSplitMember member,BigDecimal amount) { shares.add(new BillSplitShare(this,member,amount)); }
 void voidExpense() { voided=true; }
}
