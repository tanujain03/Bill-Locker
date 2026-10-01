package project.bill_locker.product;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentRepository;
import project.bill_locker.document.DocumentRepository.ProductDocumentCount;
import project.bill_locker.user.UserService;
import project.bill_locker.warranty.WarrantyStatus;

/**
 * Products and their warranties. Every method takes the signed-in user's id and only
 * ever touches that user's products.
 */
@Service
public class ProductService {

	private final ProductRepository products;
	private final CategoryRepository categories;
	private final DocumentRepository documents;
	private final UserService userService;

	public ProductService(ProductRepository products, CategoryRepository categories, DocumentRepository documents,
			UserService userService) {
		this.products = products;
		this.categories = categories;
		this.documents = documents;
		this.userService = userService;
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> listCategories() {
		return categories.findAllByOrderBySortOrderAsc().stream().map(CategoryResponse::from).toList();
	}

	/**
	 * The user's products, newest first. {@code search} matches name, brand, model, seller,
	 * serial and invoice number, ignoring case. Null filters mean "any".
	 */
	@Transactional(readOnly = true)
	public List<ProductResponse> list(UUID userId, String search, UUID categoryId, WarrantyStatus warrantyStatus) {
		String pattern = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
		Map<UUID, Long> documentCounts = documents.countPerProduct(userId).stream()
				.collect(Collectors.toMap(ProductDocumentCount::getProductId, ProductDocumentCount::getDocuments));
		LocalDate today = LocalDate.now();
		return products.findForUser(userId, categoryId, pattern).stream()
				// The status depends on today's date, so this filter runs in Java rather than in SQL.
				.filter(product -> warrantyStatus == null || statusOf(product, today) == warrantyStatus)
				.map(product -> ProductResponse.from(product, documentCounts.getOrDefault(product.getId(), 0L), today))
				.toList();
	}

	@Transactional(readOnly = true)
	public ProductResponse get(UUID userId, UUID productId) {
		return toResponse(findOwned(userId, productId));
	}

	@Transactional
	public ProductResponse create(UUID userId, ProductInput input) {
		Product product = new Product(userService.findUser(userId), input, findCategory(input.categoryId()));
		// Saving the product saves its warranty too (cascade).
		return toResponse(products.save(product));
	}

	@Transactional
	public ProductResponse update(UUID userId, UUID productId, ProductInput input) {
		Product product = findOwned(userId, productId);
		product.update(input, findCategory(input.categoryId()));
		return toResponse(product);
	}

	/**
	 * Deletes the product and its warranty. Its documents are kept, just unlinked: the
	 * database sets their product_id to null (ON DELETE SET NULL). Never lose a bill.
	 */
	@Transactional
	public void delete(UUID userId, UUID productId) {
		products.delete(findOwned(userId, productId));
	}

	/**
	 * "Confirm & Save" of a document: creates a product ({@code productId} null) or replaces
	 * the details of one of the user's products, and remembers the document as the source
	 * of its warranty. Runs inside the caller's transaction (DocumentService.confirm).
	 */
	@Transactional
	public Product saveFromDocument(UUID userId, UUID productId, ProductInput input, Document document) {
		Category category = findCategory(input.categoryId());
		Product product;
		if (productId == null) {
			product = products.save(new Product(userService.findUser(userId), input, category));
		}
		else {
			product = findOwned(userId, productId);
			product.update(input, category);
		}
		product.getWarranty().setSourceDocument(document);
		return product;
	}

	/** The product if it belongs to this user. Someone else's product looks like a missing one (404). */
	public Product findOwned(UUID userId, UUID productId) {
		return products.findByIdAndUserId(productId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND",
						"The requested product was not found."));
	}

	public ProductResponse toResponse(Product product) {
		return ProductResponse.from(product, documents.countByProductId(product.getId()), LocalDate.now());
	}

	private static WarrantyStatus statusOf(Product product, LocalDate today) {
		return product.getWarranty() == null ? WarrantyStatus.UNKNOWN : product.getWarranty().statusOn(today);
	}

	private Category findCategory(UUID categoryId) {
		if (categoryId == null) {
			return null;
		}
		return categories.findById(categoryId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
						"Please check the highlighted fields.", Map.of("categoryId", "Unknown category")));
	}
}
