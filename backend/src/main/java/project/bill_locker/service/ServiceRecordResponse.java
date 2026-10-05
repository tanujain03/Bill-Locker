package project.bill_locker.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import project.bill_locker.product.Product;

/** A service record as the API returns it (docs/api-contract.md §9). The cost is in the product's currency. */
public record ServiceRecordResponse(
		UUID id,
		UUID productId,
		String productName,
		LocalDate serviceDate,
		ServiceType serviceType,
		String serviceCenter,
		BigDecimal cost,
		String currency,
		LocalDate nextServiceDate,
		String notes,
		Instant createdAt,
		Instant updatedAt) {

	public static ServiceRecordResponse from(ServiceRecord record) {
		Product product = record.getProduct();
		return new ServiceRecordResponse(record.getId(), product.getId(), product.getName(), record.getServiceDate(),
				record.getServiceType(), record.getServiceCenter(), record.getCost(), product.getCurrency(),
				record.getNextServiceDate(), record.getNotes(), record.getCreatedAt(), record.getUpdatedAt());
	}
}
