package project.bill_locker.service;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code POST /api/service-records} and {@code PUT /api/service-records/{id}}
 * (docs/api-contract.md §9). "The next service must be after the service date"
 * compares two fields, so ServiceRecordService checks that one.
 */
public record ServiceRecordInput(
		@NotNull(message = "Choose a product")
		UUID productId,

		@NotNull(message = "Enter the service date")
		@PastOrPresent(message = "Service date cannot be in the future")
		LocalDate serviceDate,

		@NotNull(message = "Choose a service type")
		ServiceType serviceType,

		@Size(max = 120, message = "Keep it under 120 characters")
		String serviceCenter,

		@PositiveOrZero(message = "Enter a valid amount")
		@DecimalMax(value = "100000000", message = "Enter a valid amount")
		BigDecimal cost,

		LocalDate nextServiceDate,

		@Size(max = 500, message = "Keep it under 500 characters")
		String notes) {
}
