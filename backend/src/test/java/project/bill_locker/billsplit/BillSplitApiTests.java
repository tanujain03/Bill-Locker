package project.bill_locker.billsplit;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jayway.jsonpath.JsonPath;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.document.DocumentApiTestBase;
class BillSplitApiTests extends DocumentApiTestBase {
 @Autowired JdbcTemplate jdbc;
 private ResultActions postJson(String token,String path,String body) throws Exception {
  return mvc.perform(post("/api/splits"+path).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
 }
 private String json(ResultActions result) throws Exception {return result.andReturn().getResponse().getContentAsString();}
 private String token() throws Exception {return registerAndGetToken(uniqueEmail("split"));}
 private String group(String t) throws Exception {return json(postJson(t,"/groups","{\"name\":\"Goa trip\"}").andExpect(status().isCreated()));}
 private String value(String json,String path) {return JsonPath.read(json,path);}
 private String guest(String t,String id) throws Exception {return json(postJson(t,"/groups/"+id+"/members","{\"name\":\"Aman\"}").andExpect(status().isCreated()));}
 private String invite(String t,String id,String member) throws Exception {return value(json(postJson(t,"/groups/"+id+"/members/"+member+"/invitation","{}").andExpect(status().isOk())),"$.token");}
 private String tokenBody(String t) {return "{\"token\":\""+t+"\"}";}
 private String expense(String owner,String guest,String request,String source) {
  return "{\"requestId\":\""+request+"\",\"title\":\"Dinner\",\"amount\":100.01,\"expenseDate\":\"2026-10-07\",\"payerId\":\""+owner+"\",\"mode\":\"EQUAL\",\"shares\":[{\"memberId\":\""+owner+"\"},{\"memberId\":\""+guest+"\"}]"+(source==null?"":",\"sourceDocumentId\":\""+source+"\"")+"}";
 }
 private String payment(String from,String to,String amount,String request) {
  return "{\"requestId\":\""+request+"\",\"fromMemberId\":\""+from+"\",\"toMemberId\":\""+to+"\",\"amount\":"+amount+"}";
 }
 @Test void invitationsAreScopedOneTimeRevocableAndExpire() throws Exception {
  String t=token(),other=token(),g=group(t),id=value(g,"$.id"),self=value(g,"$.members[0].id");
  String joined=guest(t,id),m=value(joined,"$.members[1].id"),link=invite(t,id,m);
  assertThat(jdbc.queryForObject("select token_hash from bill_split_invitations where member_id=?",String.class,UUID.fromString(m))).isNotEqualTo(link).hasSize(64);
  postJson(other,"/invitations/preview",tokenBody(link)).andExpect(status().isOk()).andExpect(jsonPath("$.memberName").value("Aman"));
  String replacement=invite(t,id,m);postJson(other,"/invitations/accept",tokenBody(link)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/splits/groups/"+id+"/members/"+m+"/invitation").header("Authorization",bearer(t))).andExpect(status().isNoContent());
  postJson(other,"/invitations/accept",tokenBody(replacement)).andExpect(status().isGone());
  String expired=invite(t,id,m);jdbc.update("update bill_split_invitations set expires_at='2026-10-07T00:00:00Z' where member_id=?",UUID.fromString(m));
  postJson(other,"/invitations/accept",tokenBody(expired)).andExpect(status().isGone());
  link=invite(t,id,m);postJson(t,"/invitations/accept",tokenBody(link)).andExpect(status().isConflict());
  postJson(other,"/invitations/accept",tokenBody(link)).andExpect(status().isOk()).andExpect(jsonPath("$.members[1].self").value(true));
  postJson(other,"/invitations/accept",tokenBody(link)).andExpect(status().isGone());
  postJson(other,"/groups/"+id+"/members","{\"name\":\"New guest\"}").andExpect(status().isForbidden());
  postJson(t,"/groups/"+id+"/members/"+self+"/invitation","{}").andExpect(status().isConflict());
 }
 @Test void privateGroupAndAllMutationsRequireMembership() throws Exception {
  String t=token(),other=token(),g=group(t),id=value(g,"$.id"),m=value(g,"$.members[0].id");
  mvc.perform(get("/api/splits/groups/"+id).header("Authorization",bearer(other))).andExpect(status().isNotFound());
  mvc.perform(get("/api/splits/groups").header("Authorization",bearer(other))).andExpect(jsonPath("$.length()").value(0));
  for(String suffix:List.of("/members","/members/"+m+"/invitation","/expenses/"+UUID.randomUUID()+"/void","/settlements/"+UUID.randomUUID()+"/confirm"))
   postJson(other,"/groups/"+id+suffix,suffix.equals("/members")?"{\"name\":\"X\"}":"{}").andExpect(status().isNotFound());
  mvc.perform(get("/api/splits/groups")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/splits/invitations/preview").contentType(MediaType.APPLICATION_JSON).content(tokenBody("missing"))).andExpect(status().isUnauthorized());
 }
 @Test void exactSplitsIdempotencyAndVoiding() throws Exception {
  String t=token(),g=group(t),id=value(g,"$.id"),self=value(g,"$.members[0].id"),m=value(guest(t,id),"$.members[1].id"),request=UUID.randomUUID().toString();
  String body=expense(self,m,request,null),url="/groups/"+id+"/expenses";
  String result=json(postJson(t,url,body).andExpect(status().isCreated()).andExpect(jsonPath("$.expenses.length()").value(1)));
  List<Number> shares=JsonPath.read(result,"$.expenses[0].shares[*].amount");assertThat(shares.stream().map(n->new BigDecimal(n.toString())).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100.01");
  postJson(t,url,body).andExpect(status().isCreated()).andExpect(jsonPath("$.expenses.length()").value(1));
  postJson(t,url,body.replace("Dinner","Lunch")).andExpect(status().isConflict());
  postJson(t,url,body.replace(request,UUID.randomUUID().toString()).replace("EQUAL","CUSTOM")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.shares").exists());
  String e=value(result,"$.expenses[0].id");postJson(t,url+"/"+e+"/void","{}").andExpect(status().isOk()).andExpect(jsonPath("$.members[0].balance").value(0)).andExpect(jsonPath("$.expenses[0].voided").value(true));
 }
 @Test void validationRejectsFutureMoneyAndForeignMembers() throws Exception {
  String t=token(),g=group(t),id=value(g,"$.id"),self=value(g,"$.members[0].id"),m=value(guest(t,id),"$.members[1].id"),body=expense(self,m,UUID.randomUUID().toString(),null),url="/groups/"+id+"/expenses";
  postJson(t,url,body.replace("2026-10-07","2026-10-08")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.expenseDate").exists());
  for(String amount:List.of("-1","0","1.001","10000000000")) postJson(t,url,body.replace("100.01",amount)).andExpect(status().isBadRequest());
  postJson(t,url,body.replace(m,self)).andExpect(status().isBadRequest());
  postJson(t,url,body.replace(m,UUID.randomUUID().toString())).andExpect(status().isNotFound());
  postJson(t,"/groups/"+id+"/members","{\"name\":\" aman \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
 }
 @Test void receiptIsAnIndependentSnapshotWithoutPrivateFileAccess() throws Exception {
  String t=token(),other=token(),g=group(t),id=value(g,"$.id"),self=value(g,"$.members[0].id"),m=value(guest(t,id),"$.members[1].id");
  String doc=saveBill(t,"{\"documentType\":\"RECEIPT\",\"sellerName\":\"Cafe\",\"documentNumber\":\"REC-1\",\"buyerName\":\"Private buyer\",\"items\":[]}");
  String body=expense(self,m,UUID.randomUUID().toString(),doc),url="/groups/"+id+"/expenses";
  postJson(t,url,body.replace(doc,uploadPdf(t,"unsaved.pdf"))).andExpect(status().isConflict());
  postJson(t,url,body.replace(doc,uploadPdf(other,"foreign.pdf"))).andExpect(status().isNotFound());
  postJson(t,url,body).andExpect(status().isCreated()).andExpect(jsonPath("$.expenses[0].sellerName").value("Cafe")).andExpect(jsonPath("$.expenses[0].sourceDocumentId").doesNotExist());
  String link=invite(t,id,m);postJson(other,"/invitations/accept",tokenBody(link)).andExpect(status().isOk());
  mvc.perform(get("/api/documents/"+doc).header("Authorization",bearer(other))).andExpect(status().isNotFound());
  mvc.perform(delete("/api/documents/"+doc).header("Authorization",bearer(t))).andExpect(status().isNoContent());
  String shared=json(mvc.perform(get("/api/splits/groups/"+id).header("Authorization",bearer(other))).andExpect(status().isOk()).andExpect(jsonPath("$.expenses[0].documentNumber").value("REC-1")));
  assertThat(shared).doesNotContain("Private buyer",doc);
 }
 @Test void recipientConfirmsPaymentsAndDuplicateReportsCannotOverpay() throws Exception {
  String t=token(),other=token(),g=group(t),id=value(g,"$.id"),self=value(g,"$.members[0].id"),m=value(guest(t,id),"$.members[1].id");
  postJson(other,"/invitations/accept",tokenBody(invite(t,id,m))).andExpect(status().isOk());
  String result=json(postJson(t,"/groups/"+id+"/expenses",expense(self,m,UUID.randomUUID().toString(),null)).andExpect(status().isCreated()));
  Number debt=JsonPath.read(result,"$.members[1].balance");String amount=new BigDecimal(debt.toString()).negate().toString();
  String pay=payment(m,self,amount,UUID.randomUUID().toString()),url="/groups/"+id+"/settlements";
  String first=value(json(postJson(other,url,pay).andExpect(status().isCreated())),"$.settlements[0].id");
  postJson(other,url,pay).andExpect(status().isCreated()).andExpect(jsonPath("$.settlements.length()").value(1));
  String second=value(json(postJson(other,url,payment(m,self,amount,UUID.randomUUID().toString())).andExpect(status().isCreated())),"$.settlements[0].id");
  postJson(other,url+"/"+first+"/confirm","{}").andExpect(status().isForbidden());
  postJson(t,"/groups/"+id+"/expenses/"+value(result,"$.expenses[0].id")+"/void","{}").andExpect(status().isConflict());
  postJson(t,url+"/"+first+"/confirm","{}").andExpect(status().isOk()).andExpect(jsonPath("$.members[0].balance").value(0));
  postJson(t,url+"/"+first+"/confirm","{}").andExpect(status().isOk());
  postJson(t,url+"/"+second+"/confirm","{}").andExpect(status().isBadRequest());
  postJson(other,url+"/"+second+"/reject","{}").andExpect(status().isOk());
 }
 @Test void ownerCannotConfirmForJoinedRecipientAndCustomSharesMustBalance() throws Exception {
  String t=token(),other=token(),g=group(t),id=value(g,"$.id"),self=value(g,"$.members[0].id"),m=value(guest(t,id),"$.members[1].id");
  postJson(other,"/invitations/accept",tokenBody(invite(t,id,m))).andExpect(status().isOk());
  String body="{\"requestId\":\""+UUID.randomUUID()+"\",\"title\":\"Taxi\",\"amount\":100,\"expenseDate\":\"2026-10-07\",\"payerId\":\""+m+"\",\"mode\":\"CUSTOM\",\"shares\":[{\"memberId\":\""+self+"\",\"amount\":70},{\"memberId\":\""+m+"\",\"amount\":30}]}";
  postJson(t,"/groups/"+id+"/expenses",body.replace(":70",":60")).andExpect(status().isBadRequest());
  postJson(t,"/groups/"+id+"/expenses",body).andExpect(status().isCreated()).andExpect(jsonPath("$.members[0].balance").value(-70));
  String url="/groups/"+id+"/settlements",payment=value(json(postJson(t,url,payment(self,m,"70",UUID.randomUUID().toString())).andExpect(status().isCreated())),"$.settlements[0].id");
  postJson(t,url+"/"+payment+"/confirm","{}").andExpect(status().isForbidden());
  postJson(other,url+"/"+payment+"/confirm","{}").andExpect(status().isOk()).andExpect(jsonPath("$.members[0].balance").value(0));
 }
 @Test void roundingAssignsEveryPaiseDeterministically() {
  UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3);
  var result=SplitRules.equal(new BigDecimal("100.00"),List.of(c,a,b));
  assertThat(result.get(a)).isEqualByComparingTo("33.34");assertThat(result.get(b)).isEqualByComparingTo("33.33");
  assertThat(SplitRules.equal(new BigDecimal("0.01"),List.of(a,b)).get(b)).isEqualByComparingTo("0");
 }
 @Test void simultaneousInviteClaimsOnlyJoinOneAccount() throws Exception {
  String owner=token(),first=token(),second=token(),g=group(owner),id=value(g,"$.id"),m=value(guest(owner,id),"$.members[1].id");
  String invitation=invite(owner,id,m);
  var results=race(()->postJson(first,"/invitations/accept",tokenBody(invitation)).andReturn().getResponse().getStatus(),
   ()->postJson(second,"/invitations/accept",tokenBody(invitation)).andReturn().getResponse().getStatus());
  assertThat(results).containsExactlyInAnyOrder(200,410);
 }
 @Test void concurrentConfirmationsCannotCountTheSameDebtTwice() throws Exception {
  String t=token(),g=group(t),id=value(g,"$.id"),self=value(g,"$.members[0].id"),m=value(guest(t,id),"$.members[1].id");
  String result=json(postJson(t,"/groups/"+id+"/expenses",expense(self,m,UUID.randomUUID().toString(),null)).andExpect(status().isCreated()));
  Number balance=JsonPath.read(result,"$.members[1].balance");String amount=new BigDecimal(balance.toString()).negate().toString();
  String url="/groups/"+id+"/settlements";
  String a=value(json(postJson(t,url,payment(m,self,amount,UUID.randomUUID().toString())).andExpect(status().isCreated())),"$.settlements[0].id");
  String b=value(json(postJson(t,url,payment(m,self,amount,UUID.randomUUID().toString())).andExpect(status().isCreated())),"$.settlements[0].id");
  assertThat(race(()->postJson(t,url+"/"+a+"/confirm","{}").andReturn().getResponse().getStatus(),
   ()->postJson(t,url+"/"+b+"/confirm","{}").andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200,400);
  mvc.perform(get("/api/splits/groups/"+id).header("Authorization",bearer(t))).andExpect(jsonPath("$.members[0].balance").value(0));
 }
 @Test void splitTablesAreNotGrantedToTheAiRole() {
  for(String table:List.of("bill_split_groups","bill_split_members","bill_split_expenses","bill_split_settlements","bill_split_invitations"))
   assertThat(jdbc.queryForObject("select has_table_privilege('bill_locker_ai', ?, 'SELECT')",Boolean.class,table)).isFalse();
 }
 private List<Integer> race(java.util.concurrent.Callable<Integer> a,java.util.concurrent.Callable<Integer> b) throws Exception {
  var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   var first=pool.submit(()->{ready.countDown();start.await(5,java.util.concurrent.TimeUnit.SECONDS);return a.call();});
   var second=pool.submit(()->{ready.countDown();start.await(5,java.util.concurrent.TimeUnit.SECONDS);return b.call();});
   assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
   return List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));
  }
 }

 @Test void deleteGroupIsOwnerOnlyAndRemovesSharedHistoryWithoutDeletingBills() throws Exception {
  String owner=token(),friend=token(),outsider=token(),g=group(owner),id=value(g,"$.id"),self=value(g,"$.members[0].id");
  String guestId=value(guest(owner,id),"$.members[1].id"),link=invite(owner,id,guestId);
  postJson(friend,"/invitations/accept",tokenBody(link)).andExpect(status().isOk());
  String doc=saveBill(owner,"{\"documentType\":\"RECEIPT\",\"sellerName\":\"Cafe\",\"items\":[]}");
  postJson(owner,"/groups/"+id+"/expenses",expense(self,guestId,UUID.randomUUID().toString(),doc)).andExpect(status().isCreated());
  postJson(friend,"/groups/"+id+"/settlements",payment(guestId,self,"10",UUID.randomUUID().toString())).andExpect(status().isCreated());
  String keep=value(group(owner),"$.id");
  mvc.perform(delete("/api/splits/groups/"+id)).andExpect(status().isUnauthorized());
  mvc.perform(delete("/api/splits/groups/"+id).header("Authorization",bearer(outsider))).andExpect(status().isNotFound());
  mvc.perform(delete("/api/splits/groups/"+id).header("Authorization",bearer(friend))).andExpect(status().isForbidden());
  mvc.perform(delete("/api/splits/groups/"+id).header("Authorization",bearer(owner))).andExpect(status().isNoContent());
  for(String table:List.of("bill_split_members","bill_split_invitations","bill_split_expenses","bill_split_settlements","bill_split_activity"))
   assertThat(jdbc.queryForObject("select count(*) from "+table+" where group_id=?",Integer.class,UUID.fromString(id))).isZero();
  assertThat(jdbc.queryForObject("select count(*) from bill_split_shares s left join bill_split_expenses e on e.id=s.expense_id where e.id is null",Integer.class)).isZero();
  mvc.perform(get("/api/splits/groups/"+id).header("Authorization",bearer(friend))).andExpect(status().isNotFound());
  mvc.perform(get("/api/splits/groups").header("Authorization",bearer(friend))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get("/api/splits/groups/"+keep).header("Authorization",bearer(owner))).andExpect(status().isOk());
  mvc.perform(get("/api/documents/"+doc).header("Authorization",bearer(owner))).andExpect(status().isOk());
  postJson(outsider,"/invitations/preview",tokenBody(link)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/splits/groups/"+id).header("Authorization",bearer(owner))).andExpect(status().isNotFound());
 }

}
