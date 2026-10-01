package project.bill_locker.product;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code POST /api/products} and {@code PUT /api/products/{id}}, and the
 * "product" part of confirming a document (docs/api-contract.md §5, "ProductInput").
 * Only the name is required. A broken rule comes back as 400 VALIDATION_ERROR with
 * the message next to that field.
 */
public record ProductInput(
		@NotBlank(message = "Product name is required")
		@Size(max = 120, message = "Keep it under 120 characters")
		String name,

		UUID categoryId,

		@Size(max = 80, message = "Keep it under 80 characters")
		String brand,

		@Size(max = 80, message = "Keep it under 80 characters")
		String model,

		@Size(max = 80, message = "Keep it under 80 characters")
		String serialNumber,

		@PastOrPresent(message = "Purchase date cannot be in the future")
		LocalDate purchaseDate,

		@PositiveOrZero(message = "Enter a valid amount")
		@DecimalMax(value = "100000000", message = "Enter a valid amount")
		BigDecimal purchasePrice,

		/** ISO 4217 code such as INR; INR when left out. */
		@Pattern(regexp = "^[A-Z]{3}$", message = "Use a 3-letter currency code, e.g. INR")
		String currency,

		@Size(max = 120, message = "Keep it under 120 characters")
		String seller,

		@Size(max = 80, message = "Keep it under 80 characters")
		String invoiceNumber,

		/** Creates or updates the product's warranty, starting on the purchase date. */
		@Min(value = 0, message = "Enter a whole number of months (0–240)")
		@Max(value = 240, message = "Enter a whole number of months (0–240)")
		Integer warrantyMonths) {
}
