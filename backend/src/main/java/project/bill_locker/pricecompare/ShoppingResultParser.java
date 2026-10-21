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
  return unique.values().stream().limit(40).toList();
 }
 public static List<PriceComparisonView.Offer> parse(String body,String query,String model) {
  return parse(body,model).stream().map(o->{
   String match=ProductRelevance.classify(o.title(),query,model);
   return match==null?null:new PriceComparisonView.Offer(o.title(),o.seller(),o.price(),o.priceText(),o.url(),o.delivery(),match,o.conditional(),o.sponsored(),o.lookupToken(),o.directUrl());
  }).filter(Objects::nonNull).toList();
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
   offers.add(new PriceComparisonView.Offer(title,seller,price,priceText,url,text(n,"delivery",200),match,conditional,sponsored,text(n,"immersive_product_page_token",12000),null));
  }
 }

 public static String storeLink(String body,String seller) {
  JsonNode root;
  try {root=JSON.readTree(body);}catch(Exception e){throw unavailable();}
  if(root==null||root.has("error"))throw unavailable();
  String wanted=retailerDomain(seller);
  for(var store:root.path("product_results").path("stores")) {
   String url=safeMerchantLink(text(store,"link",3000));
   if(url==null||isGoogle(url))continue;
   if(wanted!=null&&hostEquals(URI.create(url).getHost(),wanted))return url;
   if(wanted==null && (hostEquals(URI.create(url).getHost(),seller.strip().replaceFirst("(?i)^www[.]","")) ||
    normalizedSeller(seller).equals(normalizedSeller(text(store,"name",120)))))return url;
  }
  return null;
 }
 private static String normalizedSeller(String value) {return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
 static String safeMerchantLink(String value) {
  String link=normalizeHttps(value);if(link==null||isGoogle(link))return null;
  String host=URI.create(link).getHost().toLowerCase(Locale.ROOT);
  if(!host.contains(".")||host.matches("[0-9.]+")||host.contains(":")||
   host.endsWith(".localhost")||host.endsWith(".local")||host.endsWith(".internal")||host.endsWith(".test")||host.endsWith(".invalid"))return null;
  return link;
 }
 static boolean isGoogle(String url) {return List.of("google.com","www.google.com","google.co.in","www.google.co.in").contains(URI.create(url).getHost().toLowerCase(Locale.ROOT));}
 private static String retailerDomain(String seller) {
  String normalized=seller.toLowerCase(Locale.ROOT).strip().replaceFirst("^www[.]","");
  return switch(normalized) {
   case "amazon","amazon.in" -> "amazon.in";
   case "flipkart","flipkart.com" -> "flipkart.com";
   case "myntra","myntra.com" -> "myntra.com";
   case "ajio","ajio.com" -> "ajio.com";
   default -> null;
  };
 }
 public static boolean modelMatches(String title,String model) {
  String clean=model.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
  if(clean.isBlank())return false;
  String pattern=clean.chars().mapToObj(c->Pattern.quote(String.valueOf((char)c)))
   .collect(java.util.stream.Collectors.joining("[\\s\\p{Punct}]*"));
  return Pattern.compile("(?i)(?<![a-z0-9])"+pattern+"(?![a-z0-9])").matcher(title).find();
 }
 static String safeLink(String value) {
  String link=normalizeHttps(value);if(link==null)return null;
  var uri=URI.create(link);String host=uri.getHost().toLowerCase(Locale.ROOT);
  boolean store=List.of("amazon.in","flipkart.com","myntra.com","ajio.com").stream().anyMatch(d->hostEquals(host,d));
  boolean google=isGoogle(link)&&(uri.getPath().startsWith("/shopping/")||uri.getPath().equals("/search"));
  return store||google?link:null;
 }
 private static String normalizeHttps(String value) {
  try {
   if(value==null||value.chars().anyMatch(Character::isISOControl))return null;
   for(char c:new char[]{' ','|','^','`','{','}','<','>','"'})value=value.replace(String.valueOf(c),"%"+Integer.toHexString(c).toUpperCase(Locale.ROOT));
   var uri=URI.create(value);
   if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))return null;
   return uri.toASCIIString();
  }catch(Exception e){return null;}
 }
 private static boolean hostEquals(String host,String domain) {host=host.toLowerCase(Locale.ROOT);return host.equals(domain)||host.endsWith("."+domain);}
 private static String text(JsonNode n,String key,int max){String s=n.path(key).asText("").strip();return s.substring(0,Math.min(max,s.length()));}
 private static ApiException unavailable(){return new ApiException(HttpStatus.BAD_GATEWAY,"PRICE_PROVIDER_ERROR","The price provider returned an unreadable response. Try later.");}
}
