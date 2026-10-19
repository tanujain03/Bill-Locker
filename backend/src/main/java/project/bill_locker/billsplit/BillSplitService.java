package project.bill_locker.billsplit;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.*;
import project.bill_locker.user.UserRepository;

@Service
@Transactional
public class BillSplitService {
 private final BillSplitGroupRepository groups;
 private final BillSplitMemberRepository members;
 private final BillSplitInvitationRepository invitations;
 private final BillSplitExpenseRepository expenses;
 private final BillSplitSettlementRepository settlements;
 private final BillSplitActivityRepository activities;
 private final UserRepository users;
 private final DocumentRepository documents;
 private final Clock clock;
 private final SecureRandom random = new SecureRandom();
 BillSplitService(BillSplitGroupRepository groups,BillSplitMemberRepository members,
  BillSplitInvitationRepository invitations,BillSplitExpenseRepository expenses,
  BillSplitSettlementRepository settlements,BillSplitActivityRepository activities,
  UserRepository users,DocumentRepository documents,Clock clock) {
  this.groups=groups;this.members=members;this.invitations=invitations;this.expenses=expenses;
  this.settlements=settlements;this.activities=activities;this.users=users;this.documents=documents;this.clock=clock;
 }
 @Transactional(readOnly=true)
 public List<SplitViews.GroupSummary> list(UUID user) {
  return groups.forUser(user).stream().map(g->new SplitViews.GroupSummary(g.getId(),g.getName(),
   g.getOwnerId().equals(user),roster(g).size())).toList();
 }
 public SplitViews.GroupDetail create(UUID user,SplitRequests.Group r) {
  var account=users.findById(user).orElseThrow(BillSplitService::missing);
  var g=groups.saveAndFlush(new BillSplitGroup(user,r.name().strip()));
  members.saveAndFlush(new BillSplitMember(g,account.getName(),user));
  log(g,user,"Group created");return detail(user,g.getId());
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public SplitViews.GroupDetail detail(UUID user,UUID id) {
  var g=groups.findById(id).orElseThrow(BillSplitService::missing);requireMember(g,user);
  var roster=roster(g);var records=expenses.findByGroupIdOrderByCreatedAtDescIdDesc(id);
  var payments=settlements.findByGroupIdOrderByCreatedAtDescIdDesc(id);var balances=balances(roster,records,payments);
  return new SplitViews.GroupDetail(id,g.getName(),g.getOwnerId().equals(user),
   roster.stream().map(m->new SplitViews.Member(m.getId(),m.getName(),m.getUserId()!=null,
    user.equals(m.getUserId()),g.getOwnerId().equals(m.getUserId()),balances.get(m.getId()))).toList(),
   records.stream().map(e->new SplitViews.Expense(e.getId(),e.getTitle(),e.getAmount(),e.getExpenseDate(),e.getPayer().getId(),
    e.getMode(),e.getShares().stream().map(s->new SplitViews.Share(s.getMember().getId(),s.getAmount())).toList(),
    e.isVoided(),e.getSellerName(),e.getDocumentNumber())).toList(),
   payments.stream().map(s->new SplitViews.Settlement(s.getId(),s.getFromMember().getId(),s.getToMember().getId(),s.getAmount(),
    s.getStatus(),s.getStatus()==SettlementStatus.PENDING && recipient(g,s,user),
    s.getStatus()==SettlementStatus.PENDING && canReject(g,s,user),s.getCreatedAt())).toList(),
   activities.findTop100ByGroupIdOrderByCreatedAtDescIdDesc(id).stream()
    .map(a->new SplitViews.Activity(a.getId(),a.getMessage(),a.getCreatedAt())).toList());
 }
 public void delete(UUID user,UUID id) {
  var g=lock(id,user);owner(g,user);
  // All shared rows cascade from the group. Private source bills have no group foreign key.
  groups.delete(g);groups.flush();
 }
 public SplitViews.GroupDetail addMember(UUID user,UUID id,SplitRequests.Member r) {
  var g=lock(id,user);owner(g,user);var list=roster(g);
  if(list.size()>=50) throw conflict("A group can have up to 50 people.");
  if(list.stream().anyMatch(m->m.getName().equalsIgnoreCase(r.name().strip())))
   throw invalid("name","Use a different name for each person.");
  members.saveAndFlush(new BillSplitMember(g,r.name(),null));log(g,user,r.name().strip()+" added as a guest");
  return detail(user,id);
 }
 public SplitViews.Invitation invite(UUID user,UUID id,UUID memberId) {
  var g=lock(id,user);owner(g,user);var m=member(g,memberId);
  if(m.getUserId()!=null) throw conflict("This person has already joined.");
  var invitation=invitations.findByMemberId(memberId).orElseGet(()->new BillSplitInvitation(g,m));
  byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  var expires=clock.instant().plus(Duration.ofDays(7));invitation.issue(hash(token),expires);invitations.saveAndFlush(invitation);
  log(g,user,"Invitation issued for "+m.getName());return new SplitViews.Invitation(token,expires,m.getName());
 }
 public void revoke(UUID user,UUID id,UUID memberId) {
  var g=lock(id,user);owner(g,user);member(g,memberId);
  invitations.findByMemberId(memberId).ifPresent(i->{i.revoke();log(g,user,"Invitation revoked");});
 }
 @Transactional(readOnly=true)
 public SplitViews.Preview preview(String token) {
  var i=validInvite(token);return new SplitViews.Preview(i.getGroup().getName(),i.getMember().getName(),i.getExpiresAt());
 }
 public SplitViews.GroupDetail accept(UUID user,String token) {
  // Group first, invitation second: concurrent acceptance and reissue cannot use stale invitation state.
  UUID id=invitations.groupForToken(hash(token)).orElseThrow(BillSplitService::missing);
  var g=groups.lock(id).orElseThrow(BillSplitService::missing);var i=validInvite(token);
  if(roster(g).stream().anyMatch(m->user.equals(m.getUserId()))) throw conflict("You already belong to this group.");
  i.getMember().claim(user);i.use(clock.instant());members.flush();
  log(g,user,i.getMember().getName()+" joined");return detail(user,id);
 }
 public SplitViews.GroupDetail expense(UUID user,UUID id,SplitRequests.Expense r) {
  var g=lock(id,user);owner(g,user);
  if(r.expenseDate().isAfter(LocalDate.now(clock))) throw invalid("expenseDate","Use today or an earlier date.");
  var payer=member(g,r.payerId());Map<UUID,BigDecimal> amounts=new LinkedHashMap<>();
  for(var share:r.shares()) {
   member(g,share.memberId());
   if(amounts.containsKey(share.memberId())) throw invalid("shares","Select each person once.");
   if(r.mode()==SplitMode.CUSTOM && share.amount()==null) throw invalid("shares","Enter every selected person's share.");
   amounts.put(share.memberId(),share.amount());
  }
  if(r.mode()==SplitMode.EQUAL) amounts=SplitRules.equal(r.amount(),amounts.keySet());
  else if(amounts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(r.amount())!=0)
   throw invalid("shares","Shares must add up to the total exactly.");
  var old=expenses.findByGroupIdAndRequestId(id,r.requestId());
  if(old.isPresent()) {
   var e=old.get();Map<UUID,BigDecimal> saved=new HashMap<>();e.getShares().forEach(s->saved.put(s.getMember().getId(),s.getAmount()));
   final Map<UUID,BigDecimal> expected=amounts;
   boolean same=saved.size()==expected.size() && saved.entrySet().stream().allMatch(s->expected.containsKey(s.getKey()) && s.getValue().compareTo(expected.get(s.getKey()))==0);
   if(!same || !e.getTitle().equals(r.title().strip()) || e.getAmount().compareTo(r.amount())!=0 ||
    !e.getExpenseDate().equals(r.expenseDate()) || !e.getPayer().getId().equals(r.payerId()) || e.getMode()!=r.mode() ||
    !Objects.equals(e.getSourceDocumentId(),r.sourceDocumentId())) throw conflict("This request was already used for a different expense.");
   return detail(user,id);
  }
  var e=new BillSplitExpense(g,r,payer);
  if(r.sourceDocumentId()!=null) {
   var d=documents.findByIdAndUserId(r.sourceDocumentId(),user).orElseThrow(BillSplitService::missing);
   if(d.getStatus()!=DocumentStatus.SAVED) throw conflict("Save the bill before sharing an expense from it.");
   e.snapshot(d.getSellerName(),d.getDocumentNumber());
  }
  amounts.forEach((m,a)->e.add(member(g,m),a.setScale(2)));expenses.saveAndFlush(e);
  log(g,user,"Expense added: "+e.getTitle());return detail(user,id);
 }
 public SplitViews.GroupDetail voidExpense(UUID user,UUID id,UUID expenseId) {
  var g=lock(id,user);owner(g,user);var e=expenses.findById(expenseId).filter(x->x.getGroup().getId().equals(id)).orElseThrow(BillSplitService::missing);
  if(!e.isVoided()) {
   if(settlements.findByGroupIdOrderByCreatedAtDescIdDesc(id).stream().anyMatch(s->s.getStatus()!=SettlementStatus.REJECTED))
    throw conflict("Expenses are locked once payments are pending or confirmed.");
   e.voidExpense();log(g,user,"Expense voided: "+e.getTitle());expenses.flush();
  }
  return detail(user,id);
 }
 public SplitViews.GroupDetail report(UUID user,UUID id,SplitRequests.Settlement r) {
  var g=lock(id,user);var from=member(g,r.fromMemberId());var to=member(g,r.toMemberId());
  if(!user.equals(from.getUserId()) && !g.getOwnerId().equals(user)) throw forbidden();
  var old=settlements.findByGroupIdAndRequestId(id,r.requestId());
  if(old.isPresent()) {
   var s=old.get();if(!s.getReportedBy().equals(user) || !s.getFromMember().getId().equals(from.getId()) ||
    !s.getToMember().getId().equals(to.getId()) || s.getAmount().compareTo(r.amount())!=0) throw conflict("This request was already used for a different payment.");
   return detail(user,id);
  }
  checkPayment(g,from,to,r.amount());settlements.saveAndFlush(new BillSplitSettlement(g,user,r,from,to));
  log(g,user,"Payment reported: "+from.getName()+" to "+to.getName()+" (awaiting confirmation)");return detail(user,id);
 }
 public SplitViews.GroupDetail decide(UUID user,UUID id,UUID settlementId,boolean confirm) {
  var g=lock(id,user);var s=settlements.findById(settlementId).filter(x->x.getGroup().getId().equals(id)).orElseThrow(BillSplitService::missing);
  if(confirm ? !recipient(g,s,user) : !canReject(g,s,user)) throw forbidden();
  var target=confirm?SettlementStatus.CONFIRMED:SettlementStatus.REJECTED;
  if(s.getStatus()==target) return detail(user,id);
  if(s.getStatus()!=SettlementStatus.PENDING) throw conflict("This payment has already been decided.");
  if(confirm) checkPayment(g,s.getFromMember(),s.getToMember(),s.getAmount());
  s.decide(target,clock.instant());settlements.flush();
  log(g,user,"Payment "+(confirm?"confirmed: ":"rejected/cancelled: ")+s.getFromMember().getName()+" to "+s.getToMember().getName());return detail(user,id);
 }
 private void checkPayment(BillSplitGroup g,BillSplitMember from,BillSplitMember to,BigDecimal amount) {
  var b=balances(roster(g),expenses.findByGroupIdOrderByCreatedAtDescIdDesc(g.getId()),settlements.findByGroupIdOrderByCreatedAtDescIdDesc(g.getId()));
  if(from.getId().equals(to.getId()) || b.get(from.getId()).negate().compareTo(amount)<0 || b.get(to.getId()).compareTo(amount)<0)
   throw invalid("amount","The amount must fit both people's outstanding balances. Refresh the group and check again.");
 }
 private static Map<UUID,BigDecimal> balances(List<BillSplitMember> roster,List<BillSplitExpense> expenses,List<BillSplitSettlement> settlements) {
  Map<UUID,BigDecimal> b=new HashMap<>();roster.forEach(m->b.put(m.getId(),new BigDecimal("0.00")));
  expenses.stream().filter(e->!e.isVoided()).forEach(e->{b.compute(e.getPayer().getId(),(k,v)->v.add(e.getAmount()));
   e.getShares().forEach(s->b.compute(s.getMember().getId(),(k,v)->v.subtract(s.getAmount())));});
  settlements.stream().filter(s->s.getStatus()==SettlementStatus.CONFIRMED).forEach(s->{
   b.compute(s.getFromMember().getId(),(k,v)->v.add(s.getAmount()));b.compute(s.getToMember().getId(),(k,v)->v.subtract(s.getAmount()));});return b;
 }
 private boolean recipient(BillSplitGroup g,BillSplitSettlement s,UUID user) {
  return user.equals(s.getToMember().getUserId()) || (s.getToMember().getUserId()==null && g.getOwnerId().equals(user));
 }
 private boolean canReject(BillSplitGroup g,BillSplitSettlement s,UUID user) {
  return recipient(g,s,user) || g.getOwnerId().equals(user) || s.getReportedBy().equals(user);
 }
 private BillSplitInvitation validInvite(String token) {
  var i=invitations.findByTokenHash(hash(token)).orElseThrow(BillSplitService::missing);
  if(i.isRevoked() || i.getUsedAt()!=null || !i.getExpiresAt().isAfter(clock.instant()) || i.getMember().getUserId()!=null)
   throw new ApiException(HttpStatus.GONE,"INVITATION_UNAVAILABLE","This invitation was used, revoked or expired. Ask the group owner for a new link.");return i;
 }
 private static String hash(String token) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
 }
 private BillSplitGroup lock(UUID id,UUID user) {var g=groups.lock(id).orElseThrow(BillSplitService::missing);requireMember(g,user);return g;}
 private void requireMember(BillSplitGroup g,UUID user) {if(roster(g).stream().noneMatch(m->user.equals(m.getUserId()))) throw missing();}
 private void owner(BillSplitGroup g,UUID user) {if(!g.getOwnerId().equals(user)) throw forbidden();}
 private List<BillSplitMember> roster(BillSplitGroup g) {return members.findByGroupIdOrderByCreatedAtAscIdAsc(g.getId());}
 private BillSplitMember member(BillSplitGroup g,UUID id) {return roster(g).stream().filter(m->m.getId().equals(id)).findFirst().orElseThrow(BillSplitService::missing);}
 private void log(BillSplitGroup g,UUID user,String message) {activities.saveAndFlush(new BillSplitActivity(g,user,message));}
 private static ApiException missing() {return new ApiException(HttpStatus.NOT_FOUND,"SPLIT_NOT_FOUND","Group, person or record not found.");}
 private static ApiException forbidden() {return new ApiException(HttpStatus.FORBIDDEN,"SPLIT_PERMISSION_REQUIRED","Only the group owner or the appropriate payment participant can do that.");}
 private static ApiException conflict(String message) {return new ApiException(HttpStatus.CONFLICT,"SPLIT_CONFLICT",message);}
 private static ApiException invalid(String field,String message) {return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message,Map.of(field,message));}
}
