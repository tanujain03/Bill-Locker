package project.bill_locker.product;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/** A product's page: one product of one bill, by the bill's id and the product's position. */
@RestController
class ProductController {

	private final ProductService products;

	ProductController(ProductService products) {
		this.products = products;
	}

	@GetMapping("/api/documents/{id}/items/{position}/product")
	ProductView product(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable int position) {
		return products.product(CurrentUser.id(jwt), id, position);
	}
}
