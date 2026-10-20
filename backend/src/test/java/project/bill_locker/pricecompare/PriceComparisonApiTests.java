package project.bill_locker.pricecompare;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.ApiTest;
import project.bill_locker.common.ApiException;
@Import(PriceComparisonApiTests.Config.class)
class PriceComparisonApiTests extends ApiTest {
 @Autowired Fake provider;
 @Autowired JdbcTemplate jdbc;
 @Autowired ShoppingSearchAttemptRepository attempts;
 @Autowired Clock clock;
 static final String RESPONSE="""
 {"shopping_results":[
 {"title":"Casio A168WA-1 Silver Watch","source":"Amazon.in","price":"₹2,495","extracted_price":2495,"product_link":"https://www.amazon.in/dp/TEST","delivery":"Free delivery"},
 {"title":"Casio A168WA-1","source":"Flipkart","price":"₹2,300","extracted_price":2300,"link":"https://www.flipkart.com/watch/p/item"}]}
 """;
 @TestConfiguration(proxyBeanMethods=false) static class Config {
  @Bean @Primary Fake fakeShoppingProvider(){return new Fake();}
 }
 static class Fake implements ShoppingProvider {
  AtomicInteger calls=new AtomicInteger();boolean enabled=true,fail=false;String response=RESPONSE;
  public boolean configured(){return enabled;}
  public String search(String query){calls.incrementAndGet();if(fail)throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"PRICE_PROVIDER_ERROR","Unavailable");return response;}
  public String stores(String token){calls.incrementAndGet();return "{\"product_results\":{\"stores\":[{\"name\":\"Amazon.in\",\"link\":\"https://www.amazon.in/dp/RESOLVED\"}]}}";}
 }
 @BeforeEach void reset(){jdbc.update("delete from price_compare_cache");jdbc.update("delete from price_compare_attempts");provider.calls.set(0);provider.enabled=true;provider.fail=false;provider.response=RESPONSE;}
 private String token() throws Exception{return registerAndGetToken(uniqueEmail("compare"));}
 private ResultActions search(String token,String query) throws Exception {
  return mvc.perform(post("/api/price-comparison/search").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
   .content("{\"query\":\""+query+"\",\"model\":\"A168WA-1\"}"));
 }
 @Test void comparesAndCachesOnlyTheCallersResults() throws Exception {
  String first=token(),second=token();
  search(first,"Casio watch").andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(false))
   .andExpect(jsonPath("$.offers[0].price").value(2300)).andExpect(jsonPath("$.offers[0].match").value("MODEL_MATCH"));
  search(first,"  Casio   watch  ").andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
  assertThat(provider.calls.get()).isEqualTo(1);
  search(second,"Casio watch").andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(false));
  assertThat(provider.calls.get()).isEqualTo(2);
  jdbc.update("update price_compare_cache set checked_at=?",java.sql.Timestamp.from(clock.instant().minus(Duration.ofMinutes(30))));
  search(first,"Casio watch").andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(false));
 }
 @Test void loginAndValidationAndMissingConfigurationDoNotSpendRequests() throws Exception {
  mvc.perform(get("/api/price-comparison/status")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/price-comparison/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"watch\"}")).andExpect(status().isUnauthorized());
  String t=token();search(t,"  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.query").exists());
  search(t,"https://example.com").andExpect(status().isBadRequest());
  provider.enabled=false;search(t,"Casio watch").andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PRICE_SEARCH_NOT_CONFIGURED"));
  assertThat(attempts.count()).isZero();
 }
 @Test void dailyCapAndFailedRequestsArePersisted() throws Exception {
  String t=token();provider.fail=true;
  for(int i=0;i<5;i++)search(t,"Casio watch "+i).andExpect(status().isBadGateway());
  search(t,"Casio watch sixth").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("PRICE_DAILY_LIMIT"));
  assertThat(attempts.count()).isEqualTo(5);assertThat(provider.calls.get()).isEqualTo(5);
 }
 @Test void sharedBudgetBlocksNewSearchesButAllowsCache() throws Exception {
  String t=token();search(t,"Casio watch").andExpect(status().isOk());
  jdbc.update("insert into price_compare_attempts (id,created_at,updated_at,user_id,attempted_at) select gen_random_uuid(),now(),now(),gen_random_uuid(),? from generate_series(1,199)",java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(2))));
  search(t,"New Casio watch").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("PRICE_SEARCH_LIMIT"));
  search(t,"Casio watch").andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
  mvc.perform(get("/api/price-comparison/status").header("Authorization",bearer(t))).andExpect(jsonPath("$.remaining").value(0));
  assertThat(provider.calls.get()).isEqualTo(1);
 }
 @Test void hourlyCapAndRollingWindowUseClock() throws Exception {
  String t=token();
  jdbc.update("insert into price_compare_attempts (id,created_at,updated_at,user_id,attempted_at) select gen_random_uuid(),now(),now(),gen_random_uuid(),? from generate_series(1,40)",java.sql.Timestamp.from(clock.instant()));
  search(t,"Casio watch").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("PRICE_HOURLY_LIMIT"));
  jdbc.update("update price_compare_attempts set attempted_at=?",java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(32))));
  search(t,"Casio watch").andExpect(status().isOk());
 }
 @Test void simultaneousSearchesCannotExceedDailyBudget() throws Exception {
  String t=token();var ready=new java.util.concurrent.CountDownLatch(6);var start=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(6)) {
   var futures=new ArrayList<java.util.concurrent.Future<Integer>>();
   for(int i=0;i<6;i++){final int n=i;futures.add(pool.submit(()->{ready.countDown();start.await(5,java.util.concurrent.TimeUnit.SECONDS);return search(t,"Casio watch "+n).andReturn().getResponse().getStatus();}));}
   assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
   List<Integer> results=new ArrayList<>();for(var f:futures)results.add(f.get(20,java.util.concurrent.TimeUnit.SECONDS));
   assertThat(results).containsExactlyInAnyOrder(200,200,200,200,200,429);
   assertThat(attempts.count()).isEqualTo(5);assertThat(provider.calls.get()).isEqualTo(5);
  }
 }

 @Test void opensOnlyOwnedCachedOffersAndCachesResolvedStoreLinks() throws Exception {
  String t=token(),other=token();
  provider.response="""
  {"shopping_results":[{"title":"Casio A168WA-1","source":"Amazon.in","price":"₹2495","extracted_price":2495,
  "product_link":"https://www.google.com/shopping/product/123","immersive_product_page_token":"saved-token"}]}
  """;
  search(t,"Casio watch").andExpect(status().isOk());
  String body="""
  {"search":{"query":"Casio watch","model":"A168WA-1"},"url":"https://www.google.com/shopping/product/123","seller":"Amazon.in"}
  """;
  mvc.perform(post("/api/price-comparison/store-link").header("Authorization",bearer(other)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
  mvc.perform(post("/api/price-comparison/store-link").header("Authorization",bearer(t)).contentType(MediaType.APPLICATION_JSON).content(body.replace("product/123","product/999"))).andExpect(status().isNotFound());
  assertThat(provider.calls.get()).isEqualTo(1);
  for(int i=0;i<2;i++)mvc.perform(post("/api/price-comparison/store-link").header("Authorization",bearer(t)).contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://www.amazon.in/dp/RESOLVED"));
  assertThat(provider.calls.get()).isEqualTo(2);assertThat(attempts.count()).isEqualTo(2);
 }
 @Test void directStoreLinksDoNotUseExtraQuotaAndUnknownTokensAreNotFetched() throws Exception {
  String t=token();search(t,"Casio watch").andExpect(status().isOk());
  String body="""
  {"search":{"query":"Casio watch","model":"A168WA-1"},"url":"https://www.amazon.in/dp/TEST","seller":"Amazon.in"}
  """;
  mvc.perform(post("/api/price-comparison/store-link").header("Authorization",bearer(t)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
  assertThat(provider.calls.get()).isEqualTo(1);
 }

 @Test void missingRetailerIsNotReplacedAndDoesNotSpendQuotaRepeatedly() throws Exception {
  String t=token();
  provider.response="""
  {"shopping_results":[{"title":"Casio A168WA-1","source":"Myntra","price":"₹2495","extracted_price":2495,
  "product_link":"https://www.google.com/shopping/product/123","immersive_product_page_token":"saved-token"}]}
  """;
  search(t,"Casio watch").andExpect(status().isOk());
  String body="""
  {"search":{"query":"Casio watch","model":"A168WA-1"},"url":"https://www.google.com/shopping/product/123","seller":"Myntra"}
  """;
  for(int i=0;i<2;i++)mvc.perform(post("/api/price-comparison/store-link").header("Authorization",bearer(t)).contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRICE_STORE_LINK_UNAVAILABLE"));
  assertThat(provider.calls.get()).isEqualTo(2);assertThat(attempts.count()).isEqualTo(2);
 }

}
