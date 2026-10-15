package project.bill_locker.product;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	record RegistrationRequest(@NotNull Boolean registered) {}

	@PutMapping("/api/documents/{id}/items/{position}/registration")
	ProductView registration(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable int position,
			@Valid @RequestBody RegistrationRequest request) {
		return products.setRegistration(CurrentUser.id(jwt), id, position, request.registered());
	}

	@GetMapping("/api/documents/{id}/items/{position}/product")
	ProductView product(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable int position) {
		return products.product(CurrentUser.id(jwt), id, position);
	}
}
