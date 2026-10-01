package project.bill_locker.warranty;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.product.Product;
import project.bill_locker.product.ProductRepository;

/** The warranties of the signed-in user's products. */
@Service
public class WarrantyService {

	/** Soonest expiry first; warranties without an expiry date (unknown) last. */
	public static final Comparator<WarrantyResponse> SOONEST_EXPIRY_FIRST = Comparator.comparing(
			warranty -> warranty.summary().expiryDate(), Comparator.nullsLast(Comparator.naturalOrder()));

	private final ProductRepository products;

	public WarrantyService(ProductRepository products) {
		this.products = products;
	}

	/** {@code status} null means all of them. */
	@Transactional(readOnly = true)
	public List<WarrantyResponse> list(UUID userId, WarrantyStatus status) {
		LocalDate today = LocalDate.now();
		return products.findForUser(userId, null, null).stream()
				.map(Product::getWarranty)
				.filter(Objects::nonNull)
				.map(warranty -> WarrantyResponse.of(warranty, today))
				.filter(warranty -> status == null || warranty.summary().status() == status)
				.sorted(SOONEST_EXPIRY_FIRST)
				.toList();
	}
}
