package project.bill_locker.document.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import project.bill_locker.document.BillCategory;
import project.bill_locker.document.DocumentDetails;
import project.bill_locker.document.DocumentItemView;
import project.bill_locker.document.DocumentType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the JSON text Gemini wrote into {@link DocumentDetails}.
 *
 * <p>We ask Gemini for clean values, but an AI sometimes answers "₹1,499.00" for a
 * number or "12th March" for a date. This class is forgiving: a value it can clean
 * up is cleaned ("₹1,499.00" → 1499.00); a value it can't understand becomes empty
 * (null) for the user to fill in, rather than failing the whole document.
 */
final class GeminiAnswerParser {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final int MAX_TEXT = 500;

	/** A number such as 1499, 1,499.00 or 1,49,999.50 (Indian grouping). */
	private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d,]*(\\.\\d+)?");

	private GeminiAnswerParser() {
	}

	static DocumentDetails parse(String json) {
		JsonNode root;
		try {
			root = JSON.readTree(json);
		} catch (JacksonException e) {
			throw unreadable();
		}
		if (root == null || !root.isObject()) {
			throw unreadable();
		}

		List<DocumentItemView> items = new ArrayList<>();
		for (JsonNode item : root.path("items")) { // a missing "items" loops zero times
			// The link's source (printed or QR code) is decided by DocumentService, which knows the QR links.
			items.add(new DocumentItemView(text(item, "productName"), text(item, "modelNumber"),
					text(item, "serialNumber"), number(item, "unitPrice"), wholeNumber(item, "warrantyPeriodMonths"),
					date(item, "warrantyStartDate"), date(item, "warrantyEndDate"), text(item, "warrantyProvider"),
					shortText(item, "brand", 100), webLink(item, "warrantyRegistrationUrl"), null));
		}
		return new DocumentDetails(documentType(root), text(root, "documentNumber"),
				text(root, "sellerName"), text(root, "sellerAddress"), text(root, "sellerContact"),
				text(root, "buyerName"), text(root, "buyerAddress"), text(root, "buyerEmail"),
				date(root, "purchaseDate"), number(root, "taxAmount"), number(root, "totalAmount"), items,
				// Only bills and receipts are sorted into categories.
				documentType(root) == DocumentType.RECEIPT ? BillCategory.parse(text(root, "category")) : null);
	}

	private static ExtractionException unreadable() {
		return new ExtractionException(ExtractionException.FAILED, "The AI's answer could not be read. Please try again.");
	}

	/** The value as trimmed text, or null when it is missing, null or blank. */
	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull() || value.isContainer()) {
			return null;
		}
		String text = value.asString().strip();
		if (text.isEmpty()) {
			return null;
		}
		// Columns hold 500 characters; a longer value is cut rather than failing the save.
		return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
	}

	/** Like text(), for a column of {@code max} characters: too long means misread → null. */
	private static String shortText(JsonNode node, String field, int max) {
		String text = text(node, field);
		return text == null || text.length() > max ? null : text;
	}

	/**
	 * Only a real web link (http/https, no spaces, fits the column). The page makes it
	 * clickable, so anything else ("javascript:…", "see box") becomes null.
	 */
	static String webLink(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isString()) {
			return null;
		}
		String link = value.asString().strip();
		if (link.startsWith("www.")) {
			link = "https://" + link; // printed without the scheme
		}
		return link.matches("(?i)^https?://\\S+$") && link.length() <= 1000 ? link : null;
	}

	/** Unknown or missing types become OTHER (or null when there's nothing at all). */
	private static DocumentType documentType(JsonNode root) {
		String text = text(root, "documentType");
		if (text == null) {
			return null;
		}
		try {
			return DocumentType.valueOf(text.toUpperCase().replace(' ', '_'));
		} catch (IllegalArgumentException e) {
			return DocumentType.OTHER;
		}
	}

	/** Dates must be YYYY-MM-DD; anything else is left empty. */
	private static LocalDate date(JsonNode node, String field) {
		String text = text(node, field);
		if (text == null) {
			return null;
		}
		try {
			return LocalDate.parse(text);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/**
	 * A number, from a JSON number or from text holding exactly one number
	 * ("₹1,499.00", "Rs. 999"). Text with several numbers ("1.2.3") is ambiguous → null.
	 */
	private static BigDecimal number(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isNumber()) {
			return fitsColumn(value.decimalValue());
		}
		String text = text(node, field);
		if (text == null) {
			return null;
		}
		Matcher m = NUMBER.matcher(text);
		if (!m.find()) {
			return null;
		}
		String found = m.group();
		if (m.find()) { // a second number: we can't tell which one is meant
			return null;
		}
		return fitsColumn(new BigDecimal(found.replace(",", "")));
	}

	/**
	 * Amounts are stored as numeric(12,2): up to 10 digits before the point. A bigger
	 * number is a misreading (e.g. a phone number read as a price) → null. Extra
	 * decimals are rounded to cents.
	 */
	private static BigDecimal fitsColumn(BigDecimal number) {
		BigDecimal cents = number.setScale(2, RoundingMode.HALF_UP);
		return cents.precision() - cents.scale() > 10 ? null : cents;
	}

	/** A whole number such as 12 or "24 months"; 1.5, "lifetime" or a huge number → null. */
	private static Integer wholeNumber(JsonNode node, String field) {
		BigDecimal number = number(node, field);
		if (number == null) {
			return null;
		}
		try {
			return number.intValueExact();
		} catch (ArithmeticException e) {
			return null;
		}
	}
}
