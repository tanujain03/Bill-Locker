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
  String key=hash(effective.toLowerCase(Locale.ROOT)+"|"+model.toLowerCase(Locale.ROOT));
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
 private static String hash(String value) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
