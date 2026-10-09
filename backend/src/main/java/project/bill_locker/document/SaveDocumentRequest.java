package project.bill_locker.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The reviewed details the user saves (PUT /api/documents/{id}). Every field is
 * optional: a bill may not show it. The annotations are the rules (they match the
 * column sizes: 500 characters, amounts up to 10 digits + 2 decimals); a broken one
 * gives 400 VALIDATION_ERROR with the field's name.
 */
public record SaveDocumentRequest(
		DocumentType documentType,
		@Size(max = 500) String documentNumber,
		@Size(max = 500) String sellerName,
		@Size(max = 500) String sellerAddress,
		@Size(max = 500) String sellerContact,
		@Size(max = 500) String buyerName,
		@Size(max = 500) String buyerAddress,
		@Size(max = 500) @Email(message = "Enter a valid email address") String buyerEmail,
		LocalDate purchaseDate,
		@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal taxAmount,
		@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal totalAmount,
		@Size(max = 50, message = "A document can have at most 50 products.") List<@Valid ItemRequest> items,
		BillCategory category) {

	public record ItemRequest(
			@Size(max = 500) String productName,
			@Size(max = 500) String modelNumber,
			@Size(max = 500) String serialNumber,
			@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
			@Min(0) @Max(600) Integer warrantyPeriodMonths,
			LocalDate warrantyStartDate,
			LocalDate warrantyEndDate,
			@Size(max = 500) String warrantyProvider,
			@Size(max = 100) String brand,
			// Only web links: the page turns this into a link, and "javascript:…" must never get there.
			@Size(max = 1000) @Pattern(regexp = "^https?://\\S+$", message = "Enter a link starting with https://")
			String registrationUrl,
			RegistrationSource registrationSource) {

		DocumentItemView toView() {
			return new DocumentItemView(productName, modelNumber, serialNumber, unitPrice, warrantyPeriodMonths,
					warrantyStartDate, warrantyEndDate, warrantyProvider, blankToNull(brand), blankToNull(registrationUrl),
					// A link without a source was typed in by the user.
					registrationUrl == null || registrationUrl.isBlank() ? null
							: registrationSource == null ? RegistrationSource.USER : registrationSource);
		}

		private static String blankToNull(String text) {
			return text == null || text.isBlank() ? null : text.strip();
		}
	}

	DocumentDetails toDetails() {
		List<DocumentItemView> views = items == null ? List.of() : items.stream().map(ItemRequest::toView).toList();
		return new DocumentDetails(documentType, documentNumber, sellerName, sellerAddress, sellerContact,
				buyerName, buyerAddress, buyerEmail, purchaseDate, taxAmount, totalAmount, views,
				documentType == DocumentType.RECEIPT ? category : null); // only bills and receipts have one
	}
}
