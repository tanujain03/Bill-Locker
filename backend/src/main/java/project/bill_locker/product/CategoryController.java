package project.bill_locker.product;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Category endpoint (docs/api-contract.md §4), used by the product forms and filters. */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

	private final ProductService productService;

	public CategoryController(ProductService productService) {
		this.productService = productService;
	}

	/** {@code GET /api/categories} — all categories, in display order. */
	@GetMapping
	public List<CategoryResponse> list() {
		return productService.listCategories();
	}
}
