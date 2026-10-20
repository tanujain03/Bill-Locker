package project.bill_locker.pricecompare;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
class SerpApiShoppingProviderTests {
 @Test void sendsOneIndiaShoppingRequestToSerpApi() {
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  var provider=new SerpApiShoppingProvider(builder.build(),"test-secret");
  server.expect(requestTo(containsString("https://serpapi.com/search.json?engine=google_shopping")))
   .andExpect(requestTo(containsString("gl=in"))).andExpect(requestTo(containsString("api_key=test-secret")))
   .andRespond(withSuccess("{\"shopping_results\":[]}",MediaType.APPLICATION_JSON));
  assertThat(provider.search("Casio A168WA-1")).contains("shopping_results");server.verify();
 }
 @Test void providerErrorsNeverRevealCredentialsOrSignTheUserOut() {
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  var provider=new SerpApiShoppingProvider(builder.build(),"test-secret");
  server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("test-secret"));
  assertThatThrownBy(()->provider.search("watch")).isInstanceOf(project.bill_locker.common.ApiException.class).hasMessageNotContaining("test-secret");server.verify();
 }
 @Test void usesFixedDetailEndpointAndTokenInsteadOfFetchingProviderSuppliedUrls() {
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  var provider=new SerpApiShoppingProvider(builder.build(),"test-secret");
  server.expect(requestTo(containsString("https://serpapi.com/search.json?engine=google_immersive_product")))
   .andExpect(requestTo(containsString("page_token=saved-token"))).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
  provider.stores("saved-token");server.verify();
 }

}
