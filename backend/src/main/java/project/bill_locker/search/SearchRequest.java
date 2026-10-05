package project.bill_locker.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/ai/search}: {@code { "query": "warranties expiring within 90 days" }}. */
public record SearchRequest(
		@NotBlank(message = "Enter a search")
		@Size(max = 200, message = "Keep it under 200 characters")
		String query) {
}
