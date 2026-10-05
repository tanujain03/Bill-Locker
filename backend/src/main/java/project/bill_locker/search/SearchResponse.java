package project.bill_locker.search;

import java.util.List;
import project.bill_locker.product.ProductResponse;

/** The answer to a search: how it was understood (filters + a sentence) and the products found. */
public record SearchResponse(String query, SearchFilters filters, String explanation, List<ProductResponse> results) {
}
