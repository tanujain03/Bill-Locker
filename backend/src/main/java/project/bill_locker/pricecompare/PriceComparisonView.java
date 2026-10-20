package project.bill_locker.pricecompare;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record PriceComparisonView(String query,String model,Instant checkedAt,boolean cached,List<Offer> offers) {
 public record Offer(String title,String seller,BigDecimal price,String priceText,String url,
  String delivery,String match,boolean conditional,boolean sponsored,String lookupToken,String directUrl) {
  public Offer withDirectUrl(String direct) {return new Offer(title,seller,price,priceText,url,delivery,match,conditional,sponsored,lookupToken,direct);}
 }
 public record StoreLink(String url) {}
 public record Status(boolean configured,int remaining,int limit,int dailyLimit,String provider) {}
}
