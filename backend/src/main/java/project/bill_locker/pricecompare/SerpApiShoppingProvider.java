package project.bill_locker.pricecompare;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.*;
import project.bill_locker.common.ApiException;
/** One fixed-host request, no retries or extra product-detail calls. Provider requires the key as a query parameter; errors never expose request URLs. */
public class SerpApiShoppingProvider implements ShoppingProvider {
 private final RestClient client;
 private final String key;
 public SerpApiShoppingProvider(RestClient client,String key) {this.client=client;this.key=key.strip();}
 @Override public boolean configured() {return !key.isBlank();}
 @Override public String search(String query) {
  try {
   return client.get().uri(b->b.scheme("https").host("serpapi.com").path("/search.json")
    .queryParam("engine","google_shopping").queryParam("q",query).queryParam("gl","in")
    .queryParam("hl","en").queryParam("api_key",key).build())
    .retrieve().body(String.class);
  } catch(RestClientResponseException e) {
   if(e.getStatusCode().value()==429) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"PRICE_PROVIDER_LIMIT","SerpApi's request limit has been reached. Check your free plan allowance.");
   throw new ApiException(HttpStatus.BAD_GATEWAY,"PRICE_PROVIDER_ERROR","Price search is unavailable. Check the backend's SerpApi configuration or try later.");
  } catch(RestClientException e) {
   // Provider bodies and exceptions may contain credentials: never return or log them.
   throw new ApiException(HttpStatus.BAD_GATEWAY,"PRICE_PROVIDER_ERROR","The price provider could not be reached. Try later.");
  }
 }
}
