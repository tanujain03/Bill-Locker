package project.bill_locker.servicerecord;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.document.DocumentApiTestBase;
import project.bill_locker.assistant.ReadOnlyQueryRunner;

class ServiceRecordApiTests extends DocumentApiTestBase {
 @Autowired JdbcTemplate jdbc;
 @Autowired ReadOnlyQueryRunner runner;
 private static final String RECORD="""
  {"type":"REPAIR","title":"Cooling repaired","serviceDate":"2026-10-01","cost":900,
  "providerName":"Care Centre","description":"Replaced fan","coveredByWarranty":false,"nextDueDate":"2026-11-06"}
  """;
 private String bill(String token) throws Exception {
  return saveBill(token,"""
   {"documentType":"INVOICE","purchaseDate":"2026-01-07","items":[{"productName":"Fridge","unitPrice":900,"warrantyPeriodMonths":12}]}
   """);
 }
 private ResultActions create(String token,String doc,String body) throws Exception {
  return mvc.perform(post("/api/documents/"+doc+"/items/0/services").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
 }
 private String add(String token,String doc,String body) throws Exception {
  return JsonPath.read(create(token,doc,body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.id");
 }
 private String productId(String token,String doc) throws Exception {
  return JsonPath.read(mvc.perform(get("/api/documents/"+doc).header("Authorization",bearer(token))).andReturn().getResponse().getContentAsString(),"$.items[0].id");
 }
 @Test void createSearchUpdateDeleteAndHistory() throws Exception {
  String token=registerAndGetToken(uniqueEmail("service-crud"));String doc=bill(token);
  String id=add(token,doc,RECORD);
  mvc.perform(get("/api/documents/"+doc+"/items/0/services").header("Authorization",bearer(token)))
   .andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$[0].dueStatus").value("DUE_SOON"));
  mvc.perform(get("/api/services?type=REPAIR&q=care&due=DUE_SOON").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/services?q=absent").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(put("/api/services/"+id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
   .content(RECORD.replace("REPAIR","WARRANTY_CLAIM").replace("900","0").replace("\"description\":", "\"claimNumber\":\"CLM-1\",\"claimStatus\":\"OPEN\",\"description\":")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.claimStatus").value("OPEN"));
  mvc.perform(delete("/api/services/"+id).header("Authorization",bearer(token))).andExpect(status().isNoContent());
  mvc.perform(get("/api/services").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(0));
 }
 @Test void fieldValidationUsesTheApplicationClock() throws Exception {
  String token=registerAndGetToken(uniqueEmail("service-validation"));String doc=bill(token);
  String[][] cases={{"2026-10-01","2026-10-08","serviceDate"},{"900","-1","cost"},
   {"2026-11-06","2026-09-30","nextDueDate"},{"\"cost\":900","\"repeatMonths\":0,\"cost\":900","repeatMonths"},
   {"\"cost\":900","\"repeatMonths\":61,\"cost\":900","repeatMonths"},{"Cooling repaired"," ","title"}};
  for(String[] c:cases) create(token,doc,RECORD.replace(c[0],c[1])).andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors."+c[2]).exists());
 }
 @Test void onlySavedBillsAndExistingProductsAcceptRecords() throws Exception {
  String token=registerAndGetToken(uniqueEmail("service-saved"));String doc=uploadPdf(token,"new.pdf");
  create(token,doc,RECORD).andExpect(status().isConflict());
  mvc.perform(post("/api/documents/"+doc+"/extract").header("Authorization",bearer(token))).andExpect(status().isOk());
  create(token,doc,RECORD).andExpect(status().isConflict());
  String saved=bill(token);
  mvc.perform(post("/api/documents/"+saved+"/items/99/services").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(RECORD)).andExpect(status().isNotFound());
  mvc.perform(get("/api/documents/"+doc+"/items/0/services").header("Authorization",bearer(token))).andExpect(status().isConflict());
 }
 @Test void isolatesUsersAndAttachmentsAndRequiresLogin() throws Exception {
  String owner=registerAndGetToken(uniqueEmail("service-owner"));String other=registerAndGetToken(uniqueEmail("service-other"));
  String doc=bill(owner);String id=add(owner,doc,RECORD);
  create(other,doc,RECORD).andExpect(status().isNotFound());
  mvc.perform(get("/api/documents/"+doc+"/items/0/services").header("Authorization",bearer(other))).andExpect(status().isNotFound());
  mvc.perform(put("/api/services/"+id).header("Authorization",bearer(other)).contentType(MediaType.APPLICATION_JSON).content(RECORD)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/services/"+id).header("Authorization",bearer(other))).andExpect(status().isNotFound());
  mvc.perform(get("/api/services").header("Authorization",bearer(other))).andExpect(jsonPath("$.length()").value(0));
  String foreign=bill(other);
  create(owner,doc,RECORD.replace("\"cost\":900","\"attachmentDocumentId\":\""+foreign+"\",\"cost\":900")).andExpect(status().isNotFound());
  mvc.perform(get("/api/services")).andExpect(status().isUnauthorized());
 }
 @Test void repeatAndProductDashboardClaimKitSummariesAndSupersession() throws Exception {
  String token=registerAndGetToken(uniqueEmail("service-summary"));String doc=bill(token);String product=productId(token,doc);
  add(token,doc,RECORD.replace("2026-10-01","2026-09-01").replace("2026-11-06","2026-10-01").replace("900","0"));
  create(token,doc,RECORD.replace("\"cost\":900","\"repeatMonths\":1,\"cost\":900"))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.nextDueDate").value("2026-11-01"));
  mvc.perform(get("/api/products/"+product).header("Authorization",bearer(token)))
   .andExpect(jsonPath("$.serviceHistory.length()").value(2)).andExpect(jsonPath("$.totalServiceCost").value(900))
   .andExpect(jsonPath("$.costPerMonth").value(200)).andExpect(jsonPath("$.lastServiceDate").value("2026-10-01"))
   .andExpect(jsonPath("$.nextServiceDue").value("2026-11-01"));
  mvc.perform(get("/api/dashboard").header("Authorization",bearer(token))).andExpect(jsonPath("$.servicesDue.length()").value(1));
  mvc.perform(get("/api/services?due=OVERDUE").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get("/api/products/"+product+"/claim-kit").header("Authorization",bearer(token))).andExpect(jsonPath("$.previousServices.length()").value(2));
 }
 @Test void remindersAreSortedAndDashboardIsLimitedToFive() throws Exception {
  String token=registerAndGetToken(uniqueEmail("service-due"));
  for(int i=0;i<7;i++) add(token,bill(token),RECORD.replace("2026-10-01","2026-09-01").replace("2026-11-06","2026-10-0"+(i+1)));
  mvc.perform(get("/api/dashboard").header("Authorization",bearer(token)))
   .andExpect(jsonPath("$.servicesDue.length()").value(5)).andExpect(jsonPath("$.servicesDue[0].nextDueDate").value("2026-10-01"));
 }
 @Test void stableProductIdentityAndAttachmentDeletionAndDocumentCascade() throws Exception {
  String token=registerAndGetToken(uniqueEmail("service-cascade"));String doc=bill(token);String product=productId(token,doc);
  String attachment=uploadPdf(token,"service.pdf");
  add(token,doc,RECORD.replace("\"cost\":900","\"attachmentDocumentId\":\""+attachment+"\",\"cost\":900"));
  mvc.perform(delete("/api/documents/"+attachment).header("Authorization",bearer(token))).andExpect(status().isNoContent());
  mvc.perform(get("/api/services").header("Authorization",bearer(token))).andExpect(jsonPath("$[0].attachmentDocumentId").isEmpty());
  mvc.perform(put("/api/documents/"+doc).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(
   "{\"items\":[{\"productName\":\"New item\"},{\"id\":\""+product+"\",\"productName\":\"Fridge\"}]}" )).andExpect(status().isOk());
  mvc.perform(get("/api/services").header("Authorization",bearer(token))).andExpect(jsonPath("$[0].itemPosition").value(1));
  mvc.perform(post("/api/documents/"+doc+"/items/0/services?productId="+product).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(RECORD)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/documents/"+doc).header("Authorization",bearer(token))).andExpect(status().isNoContent());
  mvc.perform(get("/api/services").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(0));
 }
 @Test void sampleServicesAreAddedAndRemovedWithoutTouchingRealServices() throws Exception {
  String token=registerAndGetToken(uniqueEmail("service-sample"));add(token,bill(token),RECORD);
  mvc.perform(post("/api/sample-data").header("Authorization",bearer(token))).andExpect(status().isOk());
  mvc.perform(get("/api/services").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(5));
  mvc.perform(get("/api/services?due=OVERDUE").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/services?due=DUE_SOON").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(2));
  mvc.perform(get("/api/services?type=WARRANTY_CLAIM").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(delete("/api/sample-data").header("Authorization",bearer(token))).andExpect(status().isOk());
  mvc.perform(get("/api/services").header("Authorization",bearer(token))).andExpect(jsonPath("$.length()").value(1));
 }
 @Test void aiViewUsesTheSameSignatureAndNeverGrantsBaseTableAccess() throws Exception {
  String email=uniqueEmail("service-ai");String a=registerAndGetToken(email);String b=registerAndGetToken(uniqueEmail("service-ai-other"));
  add(a,bill(a),RECORD);add(b,bill(b),RECORD.replace("Cooling repaired","Private other record"));
  UUID user=jdbc.queryForObject("select id from users where email=?",UUID.class,email);
  assertThat(runner.run(user,"SELECT title FROM ai_service_records")).extracting(r->r.get("title")).containsExactly("Cooling repaired");
  UUID other=jdbc.queryForObject("select id from users where email=(select email from users where id<>? order by created_at desc limit 1)",UUID.class,user);
  String escapedSetConfig="U&\"\\0073et_config\"";
  String forged="SELECT title FROM (SELECT "+escapedSetConfig+"('app.user_id', '"+other+"', true)) x CROSS JOIN ai_service_records";
  assertThat(runner.run(user,forged)).extracting(r->r.get("title")).doesNotContain("Private other record");
  assertThat(jdbc.queryForList("SELECT * FROM ai_service_records")).isEmpty();
  assertThatThrownBy(()->runner.run(user,"SELECT * FROM product_service_records")).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->runner.run(user,"WITH x AS (DELETE FROM product_service_records RETURNING id) SELECT * FROM x")).isInstanceOf(RuntimeException.class);
 }
}
