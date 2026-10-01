package project.bill_locker.product;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;
import project.bill_locker.warranty.WarrantyStatus;

/** Product endpoints (docs/api-contract.md §5). Every method needs a valid token. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	/** {@code GET /api/products?search=&categoryId=&warrantyStatus=} — newest first. */
	@GetMapping
	public List<ProductResponse> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) WarrantyStatus warrantyStatus) {
		return productService.list(CurrentUser.id(jwt), search, categoryId, warrantyStatus);
	}

	/** {@code GET /api/products/{id}} */
	@GetMapping("/{id}")
	public ProductResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return productService.get(CurrentUser.id(jwt), id);
	}

	/** {@code POST /api/products} — add a product by hand. {@code @Valid} checks the rules in ProductInput. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProductInput input) {
		return productService.create(CurrentUser.id(jwt), input);
	}

	/** {@code PUT /api/products/{id}} — replace all of the product's details. */
	@PutMapping("/{id}")
	public ProductResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody ProductInput input) {
		return productService.update(CurrentUser.id(jwt), id, input);
	}

	/** {@code DELETE /api/products/{id}} — its documents stay in the locker, unlinked. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		productService.delete(CurrentUser.id(jwt), id);
	}
}
