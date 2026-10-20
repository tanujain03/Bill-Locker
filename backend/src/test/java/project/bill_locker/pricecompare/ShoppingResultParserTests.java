package project.bill_locker.pricecompare;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ShoppingResultParserTests {
 @Test void parsesPaiseAndDeduplicatesAndIgnoresForeignCurrencyAndUnsafeLinks() {
  var list=ShoppingResultParser.parse("""
  {"shopping_results":[
  {"title":"Casio A168WA-1","source":"Amazon","price":"Rs. 2,499.50","link":"https://amazon.in/dp/a"},
  {"title":"Casio A168WA-1","source":"Amazon","price":"Rs. 2,499.50","link":"https://amazon.in/dp/a"},
  {"title":"Foreign","price":"$20","extracted_price":20,"link":"https://amazon.in/dp/b"},
  {"title":"Bad","price":"₹10","extracted_price":10,"link":"javascript:alert(1)"},
  {"title":"Spoof","price":"₹10","extracted_price":10,"link":"https://amazon.in.evil.example/a"},
  {"title":"Private","price":"₹10","extracted_price":10,"link":"https://127.0.0.1/a"}]}
  ""","A168WA-1");
  assertThat(list).hasSize(1);assertThat(list.getFirst().price()).isEqualByComparingTo("2499.50");
 }
 @Test void labelsConditionalPricesAndOtherModelsWithoutRecommendingThemAsIdentical() {
  var list=ShoppingResultParser.parse("""
  {"shopping_results":[
  {"title":"Casio A168WA-1","price":"₹2,000","extracted_price":2000,"product_link":"https://www.google.com/shopping/product/1"},
  {"title":"Casio A168WA-10","price":"₹1,000","extracted_price":1000,"product_link":"https://www.google.com/shopping/product/2"},
  {"title":"Casio A168WA-1 with card offer","price":"₹500","extracted_price":500,"installment":{"months":12},"product_link":"https://www.google.com/shopping/product/3"}]}
  ""","A168WA-1");
  assertThat(list.getFirst().price()).isEqualByComparingTo("2000");
  assertThat(list.get(1).match()).isEqualTo("POSSIBLE_MATCH");assertThat(list.get(2).conditional()).isTrue();
  assertThat(ShoppingResultParser.modelMatches("Casio A168WA1 silver","A168WA-1")).isTrue();
 }
 @Test void parsesCategorizedAndInlineListingsAndEmptySearches() {
  assertThat(ShoppingResultParser.parse("{}","")).isEmpty();
  assertThat(ShoppingResultParser.parse("{\"inline_shopping_results\":[{\"title\":\"Watch\",\"source\":\"Myntra\",\"price\":\"₹99\",\"extracted_price\":99,\"link\":\"https://myntra.com/watch/1/buy\"}]}","").getFirst().sponsored()).isTrue();
 }
 @Test void providerErrorsAreSanitized(){assertThatThrownBy(()->ShoppingResultParser.parse("{\"error\":\"secret-key\"}","")).hasMessageNotContaining("secret-key");}
 @Test void premiumIsNotMistakenForEmiAndUsedListingsAreFlagged() {
  var list=ShoppingResultParser.parse("""
  {"shopping_results":[
  {"title":"Premium watch","price":"₹99","extracted_price":99,"link":"https://myntra.com/watch"},
  {"title":"Watch","price":"₹50","extracted_price":50,"second_hand_condition":"Used","link":"https://flipkart.com/watch"},
  {"title":"Huge","price":"₹1e100","extracted_price":"1e100","link":"https://amazon.in/dp/a"}]}
  ""","");
  assertThat(list).hasSize(2);assertThat(list.getFirst().conditional()).isFalse();assertThat(list.get(1).conditional()).isTrue();
 }

 @Test void acceptsSerpApiGoogleLinksContainingUnescapedSearchSpaces() {
  var list=ShoppingResultParser.parse("""
  {"shopping_results":[{"title":"Casio Watch","source":"Myntra","price":"₹2,695","extracted_price":2695,
  "product_link":"https://www.google.com/search?ibp=oshop&q=Casio A168WA-1 watch&prds=catalogid:123,rds:abc|xyz"}]}
  ""","A168WA-1");
  assertThat(list).hasSize(1);assertThat(list.getFirst().url()).contains("Casio%20A168WA-1%20watch","abc%7Cxyz");
 }

 @Test void parsesRepresentativeLiveSerpApiResponseShape() throws Exception {
  try(var input=getClass().getResourceAsStream("/pricecompare/shopping-results.json")) {
   assertThat(input).isNotNull();var offers=ShoppingResultParser.parse(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),"A168WA-1");
   assertThat(offers).hasSize(3);assertThat(offers).extracting(PriceComparisonView.Offer::seller).contains("Amazon.in","Myntra");
  }
 }

 @Test void resolvesTheSelectedRetailerWithoutSwitchingStoresOrFollowingUnsafeUrls() {
  String body="""
  {"product_results":{"stores":[{"name":"Flipkart","link":"https://flipkart.com/item"},
  {"name":"Amazon","link":"https://amazon.in.evil.example/item"},
  {"name":"Amazon.in","link":"https://www.amazon.in/dp/123"}]}}
  """;
  assertThat(ShoppingResultParser.storeLink(body,"Amazon.in")).isEqualTo("https://www.amazon.in/dp/123");
  assertThat(ShoppingResultParser.storeLink(body,"Myntra")).isNull();
  assertThat(ShoppingResultParser.safeMerchantLink("https://127.0.0.1/a")).isNull();
  assertThat(ShoppingResultParser.storeLink("{\"product_results\":{\"stores\":[{\"name\":\"Ubuy\",\"link\":\"https://www.ubuy.co.in/product/123\"}]}}","ubuy.co.in")).isEqualTo("https://www.ubuy.co.in/product/123");
 }

}
