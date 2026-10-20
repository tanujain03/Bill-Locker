package project.bill_locker.pricecompare;
public interface ShoppingProvider {
 boolean configured();
 String search(String query);
 default String stores(String token) {throw new UnsupportedOperationException("Store lookup unavailable");}
}
