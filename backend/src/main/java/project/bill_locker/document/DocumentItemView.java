package project.bill_locker.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One product on a bill, as JSON (camelCase versions of the CSV's field names).
 * The last three (task 5) help register the warranty: the product's brand and the
 * brand's warranty registration page, with where that link came from.
 */
public record DocumentItemView(
		String productName,
		String modelNumber,
		String serialNumber,
		BigDecimal unitPrice,
		Integer warrantyPeriodMonths,
		LocalDate warrantyStartDate,
		LocalDate warrantyEndDate,
		String warrantyProvider,
		String brand,
		String registrationUrl,
		RegistrationSource registrationSource,
		Boolean warrantyRegistered,
		UUID id) {

	public DocumentItemView(String productName, String modelNumber, String serialNumber, BigDecimal unitPrice,
			Integer warrantyPeriodMonths, LocalDate warrantyStartDate, LocalDate warrantyEndDate, String warrantyProvider,
			String brand, String registrationUrl, RegistrationSource registrationSource, Boolean warrantyRegistered) {
		this(productName, modelNumber, serialNumber, unitPrice, warrantyPeriodMonths, warrantyStartDate,
				warrantyEndDate, warrantyProvider, brand, registrationUrl, registrationSource, warrantyRegistered, null);
	}

	public DocumentItemView {
		warrantyRegistered = Boolean.TRUE.equals(warrantyRegistered);
	}

	public DocumentItemView(String productName, String modelNumber, String serialNumber, BigDecimal unitPrice,
			Integer warrantyPeriodMonths, LocalDate warrantyStartDate, LocalDate warrantyEndDate, String warrantyProvider,
			String brand, String registrationUrl, RegistrationSource registrationSource) {
		this(productName, modelNumber, serialNumber, unitPrice, warrantyPeriodMonths, warrantyStartDate,
				warrantyEndDate, warrantyProvider, brand, registrationUrl, registrationSource, false);
	}

	/** A product without brand or registration link (most tests, and items typed in before task 5). */
	public DocumentItemView(String productName, String modelNumber, String serialNumber, BigDecimal unitPrice,
			Integer warrantyPeriodMonths, LocalDate warrantyStartDate, LocalDate warrantyEndDate, String warrantyProvider) {
		this(productName, modelNumber, serialNumber, unitPrice, warrantyPeriodMonths, warrantyStartDate, warrantyEndDate,
				warrantyProvider, null, null, null);
	}

	static DocumentItemView of(DocumentItem item) {
		return new DocumentItemView(item.getProductName(), item.getModelNumber(), item.getSerialNumber(),
				item.getUnitPrice(), item.getWarrantyPeriodMonths(), item.getWarrantyStartDate(),
				item.getWarrantyEndDate(), item.getWarrantyProvider(), item.getBrand(), item.getRegistrationUrl(),
				item.getRegistrationSource(), item.isWarrantyRegistered(), item.getId());
	}

	/** The same product with another warranty end date. */
	DocumentItemView withWarrantyEndDate(LocalDate end) {
		return new DocumentItemView(productName, modelNumber, serialNumber, unitPrice, warrantyPeriodMonths,
				warrantyStartDate, end, warrantyProvider, brand, registrationUrl, registrationSource, warrantyRegistered, id);
	}

	/** The same product with another registration link. */
	public DocumentItemView withRegistration(String url, RegistrationSource source) {
		return new DocumentItemView(productName, modelNumber, serialNumber, unitPrice, warrantyPeriodMonths,
				warrantyStartDate, warrantyEndDate, warrantyProvider, brand, url, source, warrantyRegistered, id);
	}
}
