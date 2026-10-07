package project.bill_locker.document;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One product on a bill, as JSON (camelCase versions of the CSV's field names). */
public record DocumentItemView(
		String productName,
		String modelNumber,
		String serialNumber,
		BigDecimal unitPrice,
		Integer warrantyPeriodMonths,
		LocalDate warrantyStartDate,
		LocalDate warrantyEndDate,
		String warrantyProvider) {

	static DocumentItemView of(DocumentItem item) {
		return new DocumentItemView(item.getProductName(), item.getModelNumber(), item.getSerialNumber(),
				item.getUnitPrice(), item.getWarrantyPeriodMonths(), item.getWarrantyStartDate(),
				item.getWarrantyEndDate(), item.getWarrantyProvider());
	}
}
