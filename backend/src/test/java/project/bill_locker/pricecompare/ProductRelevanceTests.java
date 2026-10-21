package project.bill_locker.pricecompare;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProductRelevanceTests {
 @Test void requiresTheRequestedBrandAndProductWords() {
  assertThat(ProductRelevance.classify("Other sunscreen SPF 50", "Sunscoop sunscreen", "")).isNull();
  assertThat(ProductRelevance.classify("Sunscoop body lotion", "Sunscoop sunscreen", "")).isNull();
  assertThat(ProductRelevance.classify("Sunscoop sunscreen SPF 50", "Sunscoop sunscreen", "")).isEqualTo("TITLE_MATCH");
  assertThat(ProductRelevance.classify("Sunscoop sunscreen", "Sunscop sunscreen", "")).isNull();
 }
 @Test void separatesMissingAndDifferentSpfOrPackSizesWithoutGuessingEquivalence() {
  assertThat(ProductRelevance.classify("Sunscoop sunscreen SPF50+ 50ml", "Sunscoop sunscreen SPF 50 50 ml", "")).isEqualTo("TITLE_MATCH");
  assertThat(ProductRelevance.classify("Sunscoop sunscreen SPF30 50ml", "Sunscoop sunscreen SPF50 50ml", "")).isEqualTo("ALTERNATIVE");
  assertThat(ProductRelevance.classify("Sunscoop sunscreen SPF50 100ml", "Sunscoop sunscreen SPF50 50ml", "")).isEqualTo("ALTERNATIVE");
  assertThat(ProductRelevance.classify("Sunscoop sunscreen SPF50 50g", "Sunscoop sunscreen SPF50 50ml", "")).isEqualTo("ALTERNATIVE");
  assertThat(ProductRelevance.classify("Sunscoop sunscreen", "Sunscoop sunscreen SPF50", "")).isEqualTo("ALTERNATIVE");
 }
 @Test void handlesEquivalentUnitsAndExactModelBoundaries() {
  assertThat(ProductRelevance.classify("Acme flour 1000g", "Acme flour 1kg", "")).isEqualTo("TITLE_MATCH");
  assertThat(ProductRelevance.classify("Casio A168WA1", "Casio", "A168WA-1")).isEqualTo("MODEL_MATCH");
  assertThat(ProductRelevance.classify("Casio A168WA10", "Casio", "A168WA-1")).isEqualTo("ALTERNATIVE");
 }
 @Test void keepsProviderOrderAndRemovesUnrelatedCheapListings() {
  var offers=ShoppingResultParser.parse("""
   {"shopping_results":[
    {"title":"Sunscoop sunscreen SPF50 50ml","price":"₹500","link":"https://amazon.in/dp/a"},
    {"title":"Other sunscreen","price":"₹10","link":"https://amazon.in/dp/b"},
    {"title":"Sunscoop sunscreen SPF50 50ml","price":"₹400","link":"https://amazon.in/dp/c"}]}
   ""","Sunscoop sunscreen SPF50 50ml", "");
  assertThat(offers).extracting(o->o.price().intValue()).containsExactly(500,400);
  assertThat(offers).allMatch(o->o.match().equals("TITLE_MATCH"));
 }
}
