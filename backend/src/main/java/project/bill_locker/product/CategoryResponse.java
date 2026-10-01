package project.bill_locker.product;

import java.util.UUID;

/** A category as the API returns it (docs/api-contract.md §4). */
public record CategoryResponse(UUID id, String name, String slug) {

	static CategoryResponse from(Category category) {
		return new CategoryResponse(category.getId(), category.getName(), category.getSlug());
	}
}
