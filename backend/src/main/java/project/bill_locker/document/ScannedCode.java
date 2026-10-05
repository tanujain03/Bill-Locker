package project.bill_locker.document;

import java.math.BigDecimal;

/**
 * A barcode or QR code found on a document, read exactly (no OCR guessing).
 *
 * @param format  the symbology, e.g. {@code QR_CODE} or {@code CODE_128}
 * @param kind    what the code is, so the app can show it sensibly
 * @param value   the text inside the code
 * @param invoice for a GST e-invoice QR: the invoice details it carries, otherwise null
 */
public record ScannedCode(String format, Kind kind, String value, EInvoice invoice) {

	public enum Kind {
		/** The signed QR code that GST e-invoices carry: invoice number, date and total. */
		GST_E_INVOICE,
		/** A web address, e.g. the brand's warranty registration page. */
		LINK,
		/** A one-dimensional barcode, e.g. a serial number sticker. */
		BARCODE,
		/** Any other QR code. */
		TEXT
	}

	/** What a GST e-invoice QR code says. {@code invoiceDate} is an ISO date (yyyy-MM-dd). */
	public record EInvoice(String invoiceNumber, String invoiceDate, BigDecimal total, String sellerGstin) {
	}
}
