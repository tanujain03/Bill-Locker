package project.bill_locker.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import project.bill_locker.product.ProductInput;

/**
 * Body of {@code POST /api/documents/{id}/confirm} (docs/api-contract.md §8): the values
 * the user checked on the review screen. {@code productId} null creates a new product;
 * otherwise that product is updated. {@code @Valid} also checks the rules inside {@code product}.
 */
public record ConfirmDocumentRequest(
		@NotNull(message = "Choose a document type")
		DocumentType documentType,

		UUID productId,

		@NotNull(message = "Enter the product details")
		@Valid
		ProductInput product) {
}
