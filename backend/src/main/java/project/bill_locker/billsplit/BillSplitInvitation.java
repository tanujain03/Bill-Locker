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
@Table(name="bill_split_invitations")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
class BillSplitInvitation extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY, optional=false)
 @JoinColumn(name="group_id", nullable=false, foreignKey=@ForeignKey(name="fk_split_invitations_group"))
 @OnDelete(action=OnDeleteAction.CASCADE) private BillSplitGroup group;
 @OneToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="member_id",nullable=false,unique=true,foreignKey=@ForeignKey(name="fk_split_invitation_member"))
 @OnDelete(action=OnDeleteAction.CASCADE) private BillSplitMember member;
 @Column(name="token_hash",nullable=false,length=64,unique=true) private String tokenHash;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="used_at") private Instant usedAt;
 @Column(nullable=false) private boolean revoked;
 BillSplitInvitation(BillSplitGroup group,BillSplitMember member) { this.group=group; this.member=member; }
 void issue(String hash,Instant expires) { tokenHash=hash; expiresAt=expires; usedAt=null; revoked=false; }
 void use(Instant now) { usedAt=now; }
 void revoke() { revoked=true; }
}
