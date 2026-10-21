package project.bill_locker.pricecompare;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative title matching, not product identity verification or spelling correction. */
final class ProductRelevance {
 private static final Set<String> FILLER=Set.of("buy","best","price","online","in","india","for","a","an","the","and","with");
 private static final Pattern SPEC=Pattern.compile("\\bspf\\s*(\\d+)\\s*\\+?|\\b(\\d+(?:[.]\\d+)?)\\s*(ml|millilitres?|milliliters?|g|gm|grams?|kg|l|litres?|liters?)\\b",Pattern.CASE_INSENSITIVE);
 private ProductRelevance() {}
 static String classify(String title,String query,String model) {
  String normalized=normalize(title), search=normalize(query);
  List<String> terms=Arrays.stream(SPEC.matcher(search).replaceAll(" ").split("[^\\p{L}\\p{N}]+"))
   .filter(t->!t.isBlank()&&!FILLER.contains(t)).distinct().toList();
  // The leading meaningful search term anchors the brand/product; never guess a misspelled brand.
  Set<String> words=new HashSet<>(Arrays.asList(normalized.split("[^\\p{L}\\p{N}]+")));
  if(!terms.isEmpty()&&!words.contains(terms.getFirst()))return null;
  boolean modelMatch=model!=null&&!model.isBlank()&&ShoppingResultParser.modelMatches(title,model);
  Set<String> modelWords=new HashSet<>(Arrays.asList((model==null?"":normalize(model)).split("[^\\p{L}\\p{N}]+")));
  long matched=terms.stream().filter(t->words.contains(t)||(modelMatch&&modelWords.contains(t))).count();
  if(!terms.isEmpty()&&matched<Math.ceil(terms.size()*0.6)&&!modelMatch)return null;
  boolean complete=matched==terms.size()&&(model==null||model.isBlank()||modelMatch);
  Set<String> actualSpecs=specs(normalized), wantedSpecs=specs(search);
  if(!actualSpecs.containsAll(wantedSpecs))complete=false;
  return complete?(modelMatch?"MODEL_MATCH":"TITLE_MATCH"):"ALTERNATIVE";
 }
 private static String normalize(String value) {return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
 private static Set<String> specs(String value) {
  Set<String> values=new HashSet<>();var matcher=SPEC.matcher(value);
  while(matcher.find()) {
   if(matcher.group(1)!=null)values.add("spf:"+matcher.group(1));
   else {
    String unit=matcher.group(3).toLowerCase(Locale.ROOT);
    var amount=new java.math.BigDecimal(matcher.group(2));
    if(unit.equals("kg")||unit.equals("l")||unit.startsWith("liter")||unit.startsWith("litre"))amount=amount.multiply(java.math.BigDecimal.valueOf(1000));
    values.add((unit.startsWith("m")||unit.equals("l")||unit.startsWith("lit")?"ml:":"g:")+amount.stripTrailingZeros().toPlainString());
   }
  }
  return values;
 }
}
