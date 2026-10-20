package project.bill_locker.pricecompare;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import project.bill_locker.common.ApiException;
import tools.jackson.databind.json.JsonMapper;
@Service
public class PriceComparisonService {
 private static final JsonMapper JSON=JsonMapper.builder().build();
 private final ShoppingProvider provider;
 private final PriceSearchQuota quota;
 private final ShoppingSearchCacheRepository cache;
 private final Clock clock;
 PriceComparisonService(ShoppingProvider provider,PriceSearchQuota quota,ShoppingSearchCacheRepository cache,Clock clock) {
  this.provider=provider;this.quota=quota;this.cache=cache;this.clock=clock;
 }
 public PriceComparisonView.Status status() {return new PriceComparisonView.Status(provider.configured(),quota.remaining(),quota.limit(),5,"SerpApi");}
 public PriceComparisonView search(UUID user,PriceComparisonRequest r) {
  String query=r.query().strip().replaceAll("\\s+"," ");
  String model=r.model()==null?"":r.model().strip().replaceAll("\\s+"," ");
  if(query.length()<3 || query.contains("://"))throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Enter a product name and model, rather than a URL.",Map.of("query","Enter at least three characters of a product name or model."));
  String effective=query.toLowerCase(Locale.ROOT).contains(model.toLowerCase(Locale.ROOT))?query:query+" "+model;
  String key=cacheKey(query,model);
  var old=cache.findFirstByUserIdAndQueryKeyOrderByCheckedAtDesc(user,key);
  if(old.isPresent()&&old.get().getCheckedAt().plus(Duration.ofMinutes(30)).isAfter(clock.instant())) {
   try {var v=JSON.readValue(old.get().getResponseJson(),PriceComparisonView.class);
    return new PriceComparisonView(query,model,v.checkedAt(),true,v.offers());
   }catch(Exception ignored){ /* A cache format change must not break searching. */ }
  }
  if(!provider.configured())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PRICE_SEARCH_NOT_CONFIGURED","Price comparison has not been enabled on this server yet.");
  quota.reserve(user); // Commits before the HTTP request; failed/uncertain requests still use a reservation.
  String raw=provider.search(effective);
  var view=new PriceComparisonView(query,model,clock.instant(),false,ShoppingResultParser.parse(raw,model));
  cache.save(new ShoppingSearchCache(user,key,view.checkedAt(),JSON.writeValueAsString(view)));
  return view;
 }

 /** Resolves only an offer from this user's cached search, never a caller-supplied provider URL/token. */
 public synchronized PriceComparisonView.StoreLink storeLink(UUID user,StoreLinkRequest r) {
  String key=cacheKey(r.search().query(),r.search().model()==null?"":r.search().model());
  var entry=cache.findFirstByUserIdAndQueryKeyOrderByCheckedAtDesc(user,key)
   .filter(c->c.getCheckedAt().plus(Duration.ofMinutes(30)).isAfter(clock.instant()))
   .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PRICE_RESULT_EXPIRED","This search has expired. Compare prices again before opening a store."));
  PriceComparisonView view;
  try {view=JSON.readValue(entry.getResponseJson(),PriceComparisonView.class);}
  catch(Exception e){throw new ApiException(HttpStatus.NOT_FOUND,"PRICE_RESULT_EXPIRED","Compare prices again before opening a store.");}
  var offer=view.offers().stream().filter(o->o.url().equals(r.url())&&o.seller().equals(r.seller())).findFirst()
   .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PRICE_OFFER_NOT_FOUND","This listing was not found in your search."));
  if(offer.directUrl()!=null&&offer.directUrl().isBlank())throw noStoreLink();
  String direct=ShoppingResultParser.safeMerchantLink(offer.directUrl()==null?offer.url():offer.directUrl());
  if(direct!=null&&!ShoppingResultParser.isGoogle(direct))return new PriceComparisonView.StoreLink(direct);
  if(offer.lookupToken()==null||offer.lookupToken().isBlank())throw noStoreLink();
  if(!provider.configured())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PRICE_SEARCH_NOT_CONFIGURED","Store lookup is not configured.");
  quota.reserve(user);
  direct=ShoppingResultParser.storeLink(provider.stores(offer.lookupToken()),offer.seller());
  String resolved=direct==null?"":direct;
  var updated=view.offers().stream().map(o->o.url().equals(offer.url())&&o.seller().equals(offer.seller())?o.withDirectUrl(resolved):o).toList();
  entry.updateResponse(JSON.writeValueAsString(new PriceComparisonView(view.query(),view.model(),view.checkedAt(),view.cached(),updated)));
  cache.save(entry);
  if(direct==null)throw noStoreLink();
  return new PriceComparisonView.StoreLink(resolved);
 }
 private static ApiException noStoreLink() {return new ApiException(HttpStatus.NOT_FOUND,"PRICE_STORE_LINK_UNAVAILABLE","A direct product link for this store is unavailable. You can view its Google listing instead.");}
 private static String cacheKey(String query,String model) {
  query=query.strip().replaceAll("\\s+"," ");model=model.strip().replaceAll("\\s+"," ");
  String effective=query.toLowerCase(Locale.ROOT).contains(model.toLowerCase(Locale.ROOT))?query:query+" "+model;
  return hash("store-links-v1|"+effective.toLowerCase(Locale.ROOT)+"|"+model.toLowerCase(Locale.ROOT));
 }
 private static String hash(String value) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
