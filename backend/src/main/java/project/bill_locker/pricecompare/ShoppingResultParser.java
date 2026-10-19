package project.bill_locker.pricecompare;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;
import project.bill_locker.common.ApiException;
/** Provider prices are observations, not checkout verification or proof of identical variants. */
public final class ShoppingResultParser {
 private static final JsonMapper JSON=JsonMapper.builder().build();
 private ShoppingResultParser() {}
 public static List<PriceComparisonView.Offer> parse(String body,String model) {
  JsonNode root;
  try {root=JSON.readTree(body);} catch(Exception e) {throw unavailable();}
  if(root==null || !root.isObject() || root.has("error")) throw unavailable();
  List<PriceComparisonView.Offer> offers=new ArrayList<>();
  collect(root.path("shopping_results"),model,false,offers);
  collect(root.path("popular_products"),model,false,offers);
  collect(root.path("shopping_ads"),model,true,offers);
  collect(root.path("inline_shopping_results"),model,true,offers);
  for(var category:root.path("categorized_shopping_results")) collect(category.path("shopping_results"),model,false,offers);
  Map<String,PriceComparisonView.Offer> unique=new LinkedHashMap<>();
  for(var o:offers) unique.putIfAbsent(o.seller()+"|"+o.url()+"|"+o.price(),o);
  return unique.values().stream().sorted(Comparator.comparing(PriceComparisonView.Offer::conditional)
   .thenComparing(o->!o.match().equals("MODEL_MATCH")).thenComparing(PriceComparisonView.Offer::price)).limit(40).toList();
 }
 private static void collect(JsonNode nodes,String model,boolean sponsored,List<PriceComparisonView.Offer> offers) {
  if(!nodes.isArray())return;
  for(var n:nodes) {
   String title=text(n,"title",250),priceText=text(n,"price",100);
   String url=safeLink(text(n,"link",3000));if(url==null)url=safeLink(text(n,"product_link",3000));
   if(title.isBlank()||url==null)continue;
   boolean inr=priceText.contains("₹") || priceText.toUpperCase(Locale.ROOT).contains("INR") ||
    Pattern.compile("(?i)^rs[. ]").matcher(priceText).find() || "INR".equalsIgnoreCase(text(n,"currency",10));
   if(!inr)continue;
   BigDecimal price;
   try {String value=n.path("extracted_price").asText("");
    if(value.isBlank())value=priceText.replaceFirst("(?i)^(₹|INR|Rs\\.?)\\s*","").replace(",","").strip();
    price=new BigDecimal(value);if(price.signum()<=0 || price.scale()>2 || price.compareTo(new BigDecimal("999999999999.99"))>0)continue;
   }catch(NumberFormatException e){continue;}
   String seller=text(n,"seller",120);if(seller.isBlank())seller=text(n,"source",120);
   if(seller.isBlank())seller=URI.create(url).getHost();
   String summary=(title+" "+priceText+" "+text(n,"condition",100)+" "+text(n,"durability",100)+" "+text(n,"second_hand_condition",100)).toLowerCase(Locale.ROOT);
   boolean conditional=n.hasNonNull("installment") || Pattern.compile("(?i)\\bemi\\b|/mo\\b|per month|with.*(card|coupon|exchange)|\\brenewed\\b|\\brefurbished\\b|pre-owned|\\bused\\b").matcher(summary).find();
   String match=model!=null&&!model.isBlank()&&modelMatches(title,model)?"MODEL_MATCH":"POSSIBLE_MATCH";
   offers.add(new PriceComparisonView.Offer(title,seller,price,priceText,url,text(n,"delivery",200),match,conditional,sponsored));
  }
 }
 public static boolean modelMatches(String title,String model) {
  String clean=model.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
  if(clean.isBlank())return false;
  String pattern=clean.chars().mapToObj(c->Pattern.quote(String.valueOf((char)c)))
   .collect(java.util.stream.Collectors.joining("[\\s\\p{Punct}]*"));
  return Pattern.compile("(?i)(?<![a-z0-9])"+pattern+"(?![a-z0-9])").matcher(title).find();
 }
 static String safeLink(String value) {
  try {
   if(value.chars().anyMatch(Character::isISOControl))return null;
   // SerpApi's Google product URLs may contain raw spaces/pipes. Preserve existing escapes.
   for(char c:new char[]{' ','|','^','`','{','}','<','>','"'})
    value=value.replace(String.valueOf(c),"%"+Integer.toHexString(c).toUpperCase(Locale.ROOT));
   var uri=URI.create(value);String host=uri.getHost();
   if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))return null;
   host=host.toLowerCase(Locale.ROOT);
   boolean store=List.of("amazon.in","flipkart.com","myntra.com","ajio.com").stream().anyMatch(d->hostEquals(uri.getHost(),d));
   boolean google=List.of("www.google.com","www.google.co.in","google.com","google.co.in").contains(host)
    && (uri.getPath().startsWith("/shopping/")||uri.getPath().equals("/search"));
   return store||google?uri.toASCIIString():null;
  }catch(Exception e){return null;}
 }
 private static boolean hostEquals(String host,String domain) {host=host.toLowerCase(Locale.ROOT);return host.equals(domain)||host.endsWith("."+domain);}
 private static String text(JsonNode n,String key,int max){String s=n.path(key).asText("").strip();return s.substring(0,Math.min(max,s.length()));}
 private static ApiException unavailable(){return new ApiException(HttpStatus.BAD_GATEWAY,"PRICE_PROVIDER_ERROR","The price provider returned an unreadable response. Try later.");}
}
