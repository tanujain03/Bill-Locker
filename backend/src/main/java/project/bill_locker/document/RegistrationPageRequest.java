package project.bill_locker.document;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/documents/{id}/registration-page}: which product, and the brand
 * the user confirmed (the one the AI detected, or the user's correction).
 */
public record RegistrationPageRequest(
		@Min(0) Integer position,
		@NotBlank(message = "Enter the brand") @Size(max = 100) String brand,
		UUID itemId) {
	@AssertTrue(message = "Choose a saved product")
	public boolean isItemSpecified() {
		return itemId != null || position != null;
	}
}
