package project.bill_locker.servicerecord;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.BaseEntity;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentItem;

/** Separate from the legacy build's populated service_records table. */
@Entity
@Table(name="product_service_records", indexes={@Index(name="idx_product_service_records_user", columnList="user_id"),
 @Index(name="idx_product_service_records_item", columnList="item_id")})
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ServiceRecord extends BaseEntity {
 @Column(name="user_id", nullable=false) private UUID userId;
 @ManyToOne(fetch=FetchType.LAZY, optional=false)
 @JoinColumn(name="document_id", nullable=false, foreignKey=@ForeignKey(name="fk_product_service_records_document"))
 @OnDelete(action=OnDeleteAction.CASCADE) private Document document;
 @ManyToOne(fetch=FetchType.LAZY, optional=false)
 @JoinColumn(name="item_id", nullable=false, foreignKey=@ForeignKey(name="fk_product_service_records_item"))
 @OnDelete(action=OnDeleteAction.CASCADE) private DocumentItem item;
 /** Original position is an audit detail; responses use the product's current position. */
 @Column(name="item_position", nullable=false) private int itemPosition;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private ServiceType type;
 @Column(nullable=false, length=200) private String title;
 @Column(length=4000) private String description;
 @Column(name="service_date", nullable=false) private LocalDate serviceDate;
 @Column(name="provider_name", length=500) private String providerName;
 @Column(name="provider_contact", length=500) private String providerContact;
 @Column(precision=12, scale=2) private BigDecimal cost;
 @Column(name="covered_by_warranty", nullable=false) private boolean coveredByWarranty;
 @Column(name="claim_number", length=200) private String claimNumber;
 @Enumerated(EnumType.STRING) @Column(name="claim_status", length=20) private ClaimStatus claimStatus;
 @Column(name="next_due_date") private LocalDate nextDueDate;
 @Column(name="repeat_months") private Integer repeatMonths;
 @ManyToOne(fetch=FetchType.LAZY)
 @JoinColumn(name="attachment_document_id", foreignKey=@ForeignKey(name="fk_product_service_records_attachment"))
 @OnDelete(action=OnDeleteAction.SET_NULL) private Document attachment;

 ServiceRecord(UUID userId, Document document, DocumentItem item) {
  this.userId=userId; this.document=document; this.item=item; this.itemPosition=item.getPosition();
 }
 void update(ServiceRecordRequest r, Document attachment) {
  type=r.type(); title=r.title().strip(); description=clean(r.description()); serviceDate=r.serviceDate();
  providerName=clean(r.providerName()); providerContact=clean(r.providerContact()); cost=r.cost();
  coveredByWarranty=Boolean.TRUE.equals(r.coveredByWarranty());
  claimNumber=type==ServiceType.WARRANTY_CLAIM ? clean(r.claimNumber()) : null;
  claimStatus=type==ServiceType.WARRANTY_CLAIM ? r.claimStatus() : null;
  repeatMonths=r.repeatMonths(); nextDueDate=ServiceRules.nextDue(serviceDate,r.nextDueDate(),repeatMonths);
  this.attachment=attachment;
 }
 private static String clean(String s) { return s==null || s.isBlank() ? null : s.strip(); }
}
