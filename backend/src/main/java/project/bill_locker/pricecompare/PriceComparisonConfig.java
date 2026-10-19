package project.bill_locker.pricecompare;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
@Configuration
class PriceComparisonConfig {
 @Bean ShoppingProvider shoppingProvider(@Value("${app.price-compare.api-key:}") String key) {
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
   .followRedirects(HttpClient.Redirect.NEVER).build());
  factory.setReadTimeout(Duration.ofSeconds(25));
  return new SerpApiShoppingProvider(RestClient.builder().requestFactory(factory).build(),key);
 }
}
