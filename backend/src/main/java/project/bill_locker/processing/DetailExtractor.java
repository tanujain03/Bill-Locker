package project.bill_locker.processing;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import project.bill_locker.document.DocumentType;
import project.bill_locker.document.ExtractionResult;
import project.bill_locker.document.ScannedCode;
import project.bill_locker.document.ScannedCode.EInvoice;
import project.bill_locker.document.ScannedCode.Kind;

/**
 * Finds bill details in plain text with simple rules (regular expressions), and lets the
 * document's barcodes and QR codes correct them (step 7).
 *
 * <p>Rules are good at values that come with a label ("Invoice No: …", "Brand : …",
 * "Grand Total …", "2 Years Warranty") and at dates. Without a "Product :" label they
 * can't really tell which line is the product, so the product name is then only a guess
 * with low confidence; a later AI step will do that part better. Each value found gets
 * a confidence from 0 to 1. Anything not found stays null, which the app shows as
 * "Not found".
 */
@Component
public class DetailExtractor {

	public ExtractionResult extract(String text) {
		return extract(text, List.of());
	}

	/**
	 * Finds the details in the text, then lets the document's barcodes and QR codes
	 * ({@link CodeReader}) correct them: a code is read exactly, OCR is not.
	 */
	public ExtractionResult extract(String text, List<ScannedCode> codes) {
		List<String> lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
		EInvoice eInvoice = codes.stream().map(ScannedCode::invoice).filter(Objects::nonNull).findFirst().orElse(null);

		// A value written as "Label : value" wins; the other rules are fallbacks.
		Found<DocumentType> documentType = eInvoice != null ? new Found<>(DocumentType.INVOICE, CODE) : guessDocumentType(text);
		Found<String> brand = firstFound(findLabelled(lines, BRAND_LABEL), findKnownBrand(text));
		Found<String> productName = firstFound(findLabelled(lines, PRODUCT_LABEL), guessProductLine(lines, brand.value()));
		Found<String> model = matchingBarcode(findCode(text, MODEL), codes);
		Found<String> serialNumber = findSerialNumber(text, codes);
		Found<LocalDate> purchaseDate = firstFound(eInvoiceDate(eInvoice), findPurchaseDate(lines));
		// The product's own price when the bill states it ("Price : 42,999"), otherwise the bill total.
		Found<BigDecimal> total = firstFound(findLabelledAmount(lines, PRICE_LABEL), eInvoiceTotal(eInvoice), findTotal(lines));
		Found<String> currency = eInvoice != null ? new Found<>("INR", CODE) : findCurrency(text);
		Found<String> seller = findSeller(lines);
		Found<String> invoiceNumber = firstFound(eInvoiceNumber(eInvoice), findInvoiceNumber(text));
		Found<Integer> warrantyMonths = findWarrantyMonths(text);

		Map<String, Double> confidence = new LinkedHashMap<>();
		documentType.addTo(confidence, "documentType");
		productName.addTo(confidence, "productName");
		brand.addTo(confidence, "brand");
		model.addTo(confidence, "model");
		serialNumber.addTo(confidence, "serialNumber");
		purchaseDate.addTo(confidence, "purchaseDate");
		total.addTo(confidence, "purchasePrice");
		currency.addTo(confidence, "currency");
		seller.addTo(confidence, "seller");
		invoiceNumber.addTo(confidence, "invoiceNumber");
		warrantyMonths.addTo(confidence, "warrantyMonths");

		String isoDate = purchaseDate.value() == null ? null : purchaseDate.value().toString();
		return new ExtractionResult(documentType.value(), productName.value(), brand.value(), model.value(),
				serialNumber.value(), isoDate, total.value(), currency.value(), seller.value(), invoiceNumber.value(),
				warrantyMonths.value(), null, confidence, codes);
	}

	// ---- Barcodes and QR codes (step 7) ------------------------------------------------
	// A code is read exactly, so a value from a code is trusted most.

	private static final double CODE = 0.95;
	/** Shop product codes (EAN/UPC): the same on every box, so never a serial number. */
	private static final Pattern MENTIONS_SERIAL = Pattern.compile("(?i)\\b(serial|s\\s*/\\s*n|imei)\\b");

	private static Found<String> eInvoiceNumber(EInvoice eInvoice) {
		return eInvoice == null || eInvoice.invoiceNumber() == null ? Found.nothing() : new Found<>(eInvoice.invoiceNumber(), CODE);
	}

	private static Found<LocalDate> eInvoiceDate(EInvoice eInvoice) {
		return eInvoice == null || eInvoice.invoiceDate() == null
				? Found.nothing()
				: new Found<>(LocalDate.parse(eInvoice.invoiceDate()), CODE);
	}

	private static Found<BigDecimal> eInvoiceTotal(EInvoice eInvoice) {
		return eInvoice == null || eInvoice.total() == null ? Found.nothing() : new Found<>(eInvoice.total(), CODE);
	}

	/**
	 * A barcode with the same characters as a code read by OCR (look-alikes such as O and
	 * 0 counted as equal) is that code, exactly: "CHF2O26-847291" becomes "CHF2026-847291".
	 */
	private static Found<String> matchingBarcode(Found<String> fromText, List<ScannedCode> codes) {
		if (fromText.value() == null) {
			return fromText;
		}
		String wanted = lookAlike(fromText.value());
		return codes.stream()
				.filter(code -> code.kind() == Kind.BARCODE || code.kind() == Kind.TEXT)
				.filter(code -> lookAlike(code.value()).equals(wanted))
				.findFirst()
				.map(code -> new Found<>(code.value(), CODE))
				.orElse(fromText);
	}

	/** The OCR serial corrected by its barcode; if OCR found none, the only serial-like barcode (please verify). */
	private static Found<String> findSerialNumber(String text, List<ScannedCode> codes) {
		Found<String> fromText = matchingBarcode(findCode(text, SERIAL_NUMBER), codes);
		if (fromText.value() != null || !MENTIONS_SERIAL.matcher(text).find()) {
			return fromText;
		}
		List<ScannedCode> candidates = codes.stream()
				.filter(code -> code.kind() == Kind.BARCODE)
				.filter(code -> containsDigit(code.value()))
				.toList();
		return candidates.size() == 1 ? new Found<>(candidates.getFirst().value(), 0.7) : fromText;
	}

	/** OCR often swaps O/0, I/L/1, S/5, B/8 and Z/2, so compare codes as if those were the same. */
	static String lookAlike(String code) {
		return code.toUpperCase(Locale.ROOT)
				.replace('O', '0').replace('I', '1').replace('L', '1')
				.replace('S', '5').replace('B', '8').replace('Z', '2')
				.replaceAll("[^A-Z0-9]", "");
	}

	/** A value found by a rule, and how sure the rule is about it (0 to 1). */
	record Found<T>(T value, double confidence) {

		static <T> Found<T> nothing() {
			return new Found<>(null, 0);
		}

		void addTo(Map<String, Double> scores, String field) {
			if (value != null) {
				scores.put(field, confidence);
			}
		}
	}

	@SafeVarargs
	private static <T> Found<T> firstFound(Found<T>... options) {
		for (Found<T> option : options) {
			if (option.value() != null) {
				return option;
			}
		}
		return Found.nothing();
	}

	// ---- "Label : value" lines -----------------------------------------------------------
	// Many bills list the details as "Label : value", e.g. a "Product Details" box.
	// These are the clearest values on a bill, so they are trusted most.

	private static Pattern labelled(String labels) {
		return Pattern.compile("(?i)^(?:" + labels + ")\\s*[:\\-–—=]\\s*(?<value>.+)$");
	}

	private static final Pattern PRODUCT_LABEL = labelled("product(?:\\s*name)?|item(?:\\s*name)?|description|particulars");
	private static final Pattern BRAND_LABEL = labelled("brand(?:\\s*name)?|make");
	// Not "MRP": that is the maximum retail price, not what was paid.
	private static final Pattern PRICE_LABEL = labelled("(?:purchase\\s*|product\\s*|unit\\s*|selling\\s*)?price");

	/** The value after the label; it must contain a letter ("Item : 2" is not a product). */
	private static Found<String> findLabelled(List<String> lines, Pattern label) {
		for (String line : lines) {
			Matcher m = label.matcher(line);
			if (m.find()) {
				String value = m.group("value").replaceAll("[,;:]+$", "").strip();
				if (value.length() >= 2 && value.chars().anyMatch(Character::isLetter)) {
					return new Found<>(shorten(value), 0.85);
				}
			}
		}
		return Found.nothing();
	}

	private static Found<BigDecimal> findLabelledAmount(List<String> lines, Pattern label) {
		for (String line : lines) {
			Matcher m = label.matcher(line);
			if (m.find()) {
				BigDecimal amount = lastAmount(m.group("value"));
				if (amount != null) {
					return new Found<>(amount, 0.85);
				}
			}
		}
		return Found.nothing();
	}

	// ---- Document type -------------------------------------------------------------------

	private static Found<DocumentType> guessDocumentType(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		if (containsAny(lower, "tax invoice", "invoice no", "invoice number", "invoice date", "bill of supply", "cash memo")) {
			return new Found<>(DocumentType.INVOICE, 0.85);
		}
		if (containsAny(lower, "warranty card", "warranty certificate")) {
			return new Found<>(DocumentType.WARRANTY_CARD, 0.8);
		}
		if (containsAny(lower, "job card", "job sheet", "service report", "service receipt")) {
			return new Found<>(DocumentType.SERVICE_RECEIPT, 0.7);
		}
		if (lower.contains("repair")) {
			return new Found<>(DocumentType.REPAIR_RECEIPT, 0.6);
		}
		if (containsAny(lower, "invoice", "receipt", "bill")) {
			return new Found<>(DocumentType.INVOICE, 0.6);
		}
		return new Found<>(DocumentType.OTHER, 0.4);
	}

	// ---- Invoice number ------------------------------------------------------------------

	/**
	 * Between a label and its value: spaces and punctuation, including the stray marks OCR
	 * often adds (e.g. "InvoiceNo. —-:_ INV-2026-0915-1042").
	 */
	private static final String SEPARATOR = "[\\s:#.\\-–—_=]*";

	/** "Invoice No: ME/2026-27/006745", "Bill No. 1234", "Order ID: 403-1234567-1234567" */
	private static final Pattern INVOICE_NUMBER = Pattern.compile(
			"(?i)\\b(invoice|inv|bill|receipt|order)\\s*(?:no\\b|number\\b|num\\b|id\\b|#)" + SEPARATOR + "([A-Z0-9][A-Z0-9/\\-]{2,29})");

	/** An invoice number wins over a bill, receipt or order number. */
	private static Found<String> findInvoiceNumber(String text) {
		Found<String> otherNumber = Found.nothing();
		Matcher matcher = INVOICE_NUMBER.matcher(text);
		while (matcher.find()) {
			String value = matcher.group(2);
			if (!containsDigit(value)) {
				continue;
			}
			if (matcher.group(1).toLowerCase(Locale.ROOT).startsWith("inv")) {
				return new Found<>(value, 0.85);
			}
			if (otherNumber.value() == null) {
				otherNumber = new Found<>(value, 0.7);
			}
		}
		return otherNumber;
	}

	// ---- Purchase date -------------------------------------------------------------------

	private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
	/** 27/09/2026, 27-09-26, 27.09.2026: day first, as on Indian bills. */
	private static final Pattern NUMERIC_DATE = Pattern.compile("\\b(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})\\b");
	/** 27 Sep 2026, 27-Sept-2026, 3rd August 2026 */
	private static final Pattern DAY_MONTH_DATE = Pattern.compile(
			"(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s\\-]+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?[\\s\\-,]+(\\d{4})\\b");
	/** Sep 27, 2026 */
	private static final Pattern MONTH_DAY_DATE = Pattern.compile(
			"(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})\\b");
	/** Words that say a date is the purchase date. */
	private static final Pattern PURCHASE_DATE_LABEL = Pattern.compile(
			"(?i)(invoice|bill|order|purchase|transaction|txn)\\s*date|date\\s*of\\s*(purchase|invoice|sale)|\\bdated\\b");
	private static final List<String> MONTHS =
			List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");

	/** A date next to a purchase-date label; otherwise the first date in the document (less sure). */
	private static Found<LocalDate> findPurchaseDate(List<String> lines) {
		Found<LocalDate> firstDate = Found.nothing();
		for (int i = 0; i < lines.size(); i++) {
			boolean labelled = PURCHASE_DATE_LABEL.matcher(lines.get(i)).find();
			List<LocalDate> dates = datesIn(lines.get(i));
			if (labelled && dates.isEmpty() && i + 1 < lines.size()) {
				dates = datesIn(lines.get(i + 1)); // the label and the date on separate lines
			}
			if (dates.isEmpty()) {
				continue;
			}
			if (labelled) {
				return new Found<>(dates.get(0), 0.85);
			}
			if (firstDate.value() == null) {
				firstDate = new Found<>(dates.get(0), 0.6);
			}
		}
		return firstDate;
	}

	static List<LocalDate> datesIn(String line) {
		List<LocalDate> dates = new ArrayList<>();
		Matcher m = ISO_DATE.matcher(line);
		while (m.find()) {
			addIfValid(dates, toInt(m.group(1)), toInt(m.group(2)), toInt(m.group(3)));
		}
		m = NUMERIC_DATE.matcher(line);
		while (m.find()) {
			int first = toInt(m.group(1));
			int second = toInt(m.group(2));
			int year = toYear(m.group(3));
			// Day first, unless the second number can't be a month (09/27/2026 is month first).
			if (second > 12) {
				addIfValid(dates, year, first, second);
			}
			else {
				addIfValid(dates, year, second, first);
			}
		}
		m = DAY_MONTH_DATE.matcher(line);
		while (m.find()) {
			addIfValid(dates, toInt(m.group(3)), monthNumber(m.group(2)), toInt(m.group(1)));
		}
		m = MONTH_DAY_DATE.matcher(line);
		while (m.find()) {
			addIfValid(dates, toInt(m.group(3)), monthNumber(m.group(1)), toInt(m.group(2)));
		}
		return dates;
	}

	private static void addIfValid(List<LocalDate> dates, int year, int month, int day) {
		try {
			LocalDate date = LocalDate.of(year, month, day);
			// A purchase date is not in the future, and not before the 2000s.
			if (date.getYear() >= 2000 && !date.isAfter(LocalDate.now().plusDays(1))) {
				dates.add(date);
			}
		}
		catch (DateTimeException notARealDate) {
			// e.g. 31/02/2026
		}
	}

	private static int toYear(String digits) {
		int year = toInt(digits);
		return digits.length() == 2 ? 2000 + year : year;
	}

	private static int monthNumber(String name) {
		return MONTHS.indexOf(name.substring(0, 3).toLowerCase(Locale.ROOT)) + 1;
	}

	// ---- Total amount --------------------------------------------------------------------

	/** 8999 · 8,999.00 · 1,23,456.50 (Indian grouping) */
	private static final Pattern AMOUNT = Pattern.compile(
			// Not inside another number: no digit or comma just before, and no "9." (a decimal point) —
			// but "Rs.24,990" is fine: there the dot belongs to "Rs."
			"(?<![\\d,])(?<!\\d\\.)(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)(?![\\d,]|\\.\\d)");
	private static final Pattern GRAND_TOTAL = Pattern.compile(
			"(?i)grand\\s*total|total\\s*amount|amount\\s*payable|(net|total)\\s*payable|invoice\\s*(total|value|amount)|amount\\s*due|net\\s*amount");
	private static final Pattern TOTAL = Pattern.compile("(?i)\\btotal\\b");
	/** Lines that say "total" but are not the bill's total. */
	private static final Pattern NOT_THE_TOTAL = Pattern.compile(
			"(?i)sub\\s*-?\\s*total|total\\s*(tax|gst|qty|quantity|items?|discount|savings|weight)|tax\\s*total|taxable");

	/** The biggest amount on a "Grand Total"-like line; otherwise on a plain "Total" line (less sure). */
	private static Found<BigDecimal> findTotal(List<String> lines) {
		BigDecimal grandTotal = null;
		BigDecimal plainTotal = null;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			boolean grand = GRAND_TOTAL.matcher(line).find();
			if ((!grand && !TOTAL.matcher(line).find()) || NOT_THE_TOTAL.matcher(line).find()) {
				continue;
			}
			BigDecimal amount = lastAmount(line);
			if (amount == null && i + 1 < lines.size()) {
				amount = lastAmount(lines.get(i + 1)); // the label and the amount on separate lines
			}
			if (amount == null) {
				continue;
			}
			if (grand) {
				grandTotal = grandTotal == null ? amount : grandTotal.max(amount);
			}
			else {
				plainTotal = plainTotal == null ? amount : plainTotal.max(amount);
			}
		}
		if (grandTotal != null) {
			return new Found<>(grandTotal, 0.85);
		}
		return plainTotal != null ? new Found<>(plainTotal, 0.65) : Found.nothing();
	}

	/** The last amount on the line: totals are written at the end. */
	private static BigDecimal lastAmount(String line) {
		BigDecimal last = null;
		Matcher m = AMOUNT.matcher(line);
		while (m.find()) {
			BigDecimal amount = new BigDecimal(m.group(1).replace(",", ""));
			if (amount.compareTo(BigDecimal.ONE) >= 0) {
				last = amount;
			}
		}
		return last;
	}

	private static final Pattern RUPEES = Pattern.compile("(?i)₹|\\b(rs\\.?|inr)(?=\\s*\\d)|\\brupees?\\b");
	private static final Pattern DOLLARS = Pattern.compile("(?i)\\$\\s*\\d|\\busd\\b");
	/** GST is India's sales tax, so a bill that mentions it is in rupees (OCR often misreads ₹ as %). */
	private static final Pattern INDIAN_TAX = Pattern.compile("(?i)\\b(gstin|gst|cgst|sgst|igst)\\b");

	private static Found<String> findCurrency(String text) {
		if (RUPEES.matcher(text).find()) {
			return new Found<>("INR", 0.8);
		}
		if (DOLLARS.matcher(text).find()) {
			return new Found<>("USD", 0.7);
		}
		if (INDIAN_TAX.matcher(text).find()) {
			return new Found<>("INR", 0.6);
		}
		return Found.nothing();
	}

	// ---- Warranty period -----------------------------------------------------------------

	private static final String COUNT = "(\\d{1,3}|one|two|three|four|five|six|seven|eight|nine|ten)";
	private static final String UNIT = "(years?|yrs?|months?|mths?)";
	/** "2 Years Warranty", "24 months manufacturer warranty" */
	private static final Pattern COUNT_THEN_WARRANTY = Pattern.compile(
			"(?i)\\b" + COUNT + "\\s*-?\\s*" + UNIT + "\\b[^\\n]{0,40}?\\bwarranty");
	/** "Warranty: 1 Year", "Warranty period - 24 months" */
	private static final Pattern WARRANTY_THEN_COUNT = Pattern.compile(
			"(?i)\\bwarranty\\b[^\\n\\d]{0,30}?\\b" + COUNT + "\\s*-?\\s*" + UNIT + "\\b");
	private static final List<String> COUNT_WORDS =
			List.of("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten");

	private static Found<Integer> findWarrantyMonths(String text) {
		List<Integer> periods = new ArrayList<>();
		for (Pattern pattern : List.of(COUNT_THEN_WARRANTY, WARRANTY_THEN_COUNT)) {
			Matcher m = pattern.matcher(text);
			while (m.find()) {
				String count = m.group(1).toLowerCase(Locale.ROOT);
				int number = Character.isDigit(count.charAt(0)) ? toInt(count) : COUNT_WORDS.indexOf(count) + 1;
				int months = m.group(2).toLowerCase(Locale.ROOT).startsWith("y") ? number * 12 : number;
				if (months >= 1 && months <= 240) {
					periods.add(months);
				}
			}
		}
		if (periods.isEmpty()) {
			return Found.nothing();
		}
		// Several periods ("1 year, 10 years on compressor"): the shortest usually covers the whole product.
		boolean onlyOne = periods.stream().distinct().count() == 1;
		return new Found<>(Collections.min(periods), onlyOne ? 0.8 : 0.6);
	}

	// ---- Model and serial number ---------------------------------------------------------

	/** "Model No: HD9252/90", "Model: PS-Q19YNZE" */
	private static final Pattern MODEL = Pattern.compile(
			"(?i)\\bmodel\\s*(?:no\\b|number\\b|name\\b|#)?" + SEPARATOR + "([A-Z0-9][A-Z0-9\\-/.]{2,29})");
	/** "Serial No: PH9252X77821", "S/N 305KAXY4M512", "IMEI: 356789104512345" */
	private static final Pattern SERIAL_NUMBER = Pattern.compile(
			"(?i)\\b(?:serial\\s*(?:no\\b|number\\b|#)?|s\\s*/\\s*n\\b|imei\\s*(?:no\\b|number\\b|1\\b)?)" + SEPARATOR
					+ "([A-Z0-9][A-Z0-9\\-]{4,29})");

	/** The first labelled code that contains a digit: codes are never plain words. */
	private static Found<String> findCode(String text, Pattern pattern) {
		Matcher m = pattern.matcher(text);
		while (m.find()) {
			String value = m.group(1).replaceAll("[.\\-/]+$", "");
			if (containsDigit(value)) {
				return new Found<>(value, 0.8);
			}
		}
		return Found.nothing();
	}

	// ---- Brand, product name and seller ---------------------------------------------------

	/** Well-known brands. Short ones (LG, HP …) only count when written in capitals. */
	private static final List<String> BRANDS = List.of("Samsung", "LG", "Sony", "Apple", "Dell", "HP", "Lenovo", "Asus",
			"Acer", "Whirlpool", "Philips", "Bosch", "Godrej", "Voltas", "Daikin", "Panasonic", "Xiaomi", "Redmi",
			"OnePlus", "Realme", "Oppo", "Vivo", "Bajaj", "Havells", "Prestige", "Haier", "IFB", "Hitachi", "Blue Star",
			"Lloyd", "Carrier", "Kent", "Crompton", "Usha", "Orient", "Morphy Richards", "Canon", "Nikon", "JBL", "Bose",
			"TCL", "Motorola", "Nokia", "Microsoft", "Logitech", "Dyson", "Eureka Forbes", "Pigeon", "Butterfly");
	private static final Map<String, Pattern> BRAND_PATTERNS = BRANDS.stream().collect(Collectors.toMap(
			Function.identity(),
			brand -> Pattern.compile((brand.length() <= 3 ? "" : "(?i)") + "\\b" + Pattern.quote(brand) + "\\b")));

	/** The well-known brand mentioned most often (the earliest one when tied). */
	private static Found<String> findKnownBrand(String text) {
		String best = null;
		int bestCount = 0;
		int bestPosition = Integer.MAX_VALUE;
		for (String brand : BRANDS) {
			Matcher m = BRAND_PATTERNS.get(brand).matcher(text);
			int count = 0;
			int firstPosition = -1;
			while (m.find()) {
				if (count++ == 0) {
					firstPosition = m.start();
				}
			}
			if (count > bestCount || (count > 0 && count == bestCount && firstPosition < bestPosition)) {
				best = brand;
				bestCount = count;
				bestPosition = firstPosition;
			}
		}
		return best == null ? Found.nothing() : new Found<>(best, bestCount > 1 ? 0.75 : 0.6);
	}

	/** Company, address and label lines are not the product line. */
	private static final Pattern NOT_A_PRODUCT_LINE = Pattern.compile(
			"(?i)\\b(pvt|private|ltd|limited|llp|inc|gstin|address|www|http|warranty|brand)\\b|@");

	/** A guess (low confidence): the first line naming the brand that isn't a company or address line. */
	private static Found<String> guessProductLine(List<String> lines, String brand) {
		if (brand == null) {
			return Found.nothing();
		}
		// The brand may come from a "Brand :" label, so it is not always in the known list.
		Pattern brandPattern = BRAND_PATTERNS.getOrDefault(brand, Pattern.compile("(?i)\\b" + Pattern.quote(brand) + "\\b"));
		for (String line : lines) {
			if (!brandPattern.matcher(line).find() || NOT_A_PRODUCT_LINE.matcher(line).find()) {
				continue;
			}
			String name = line
					.replaceFirst("(?i)^(product(\\s*name)?|item|description|particulars)\\s*[:\\-]\\s*", "") // "Product: …"
					.replaceFirst("^\\d{1,3}[.)]?\\s+", "") // a row number at the start
					.replaceAll("(?i)(\\s+(₹|\\$|rs\\.?|inr)?\\s?[\\d,.%]+)+$", "") // quantity and prices (₹ 1,499 / Rs. 1,499) at the end
					.strip();
			if (name.length() >= brand.length() + 3) {
				return new Found<>(shorten(name), 0.5);
			}
		}
		return Found.nothing();
	}

	/** "Sold By: Metro Electronics", or "Sold By :" with the name on the next line (not "Sellers …"). */
	private static final Pattern SELLER_LABEL = Pattern.compile(
			"(?i)^(?:sold\\s*by|seller(?:\\s*name)?|retailer|dealer(?:\\s*name)?)\\b\\s*[:\\-]?\\s*(?<name>.*)$");

	private static Found<String> findSeller(List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			Matcher m = SELLER_LABEL.matcher(lines.get(i));
			if (!m.find()) {
				continue;
			}
			String name = m.group("name").strip();
			double confidence = 0.85; // the name right after the label
			if (name.isEmpty() && i + 1 < lines.size()) {
				name = lines.get(i + 1);
				confidence = 0.65; // the name on the next line: less sure
			}
			name = name.replaceAll("[,;:]+$", "").strip();
			if (name.length() >= 3) {
				return new Found<>(shorten(name), confidence);
			}
		}
		return Found.nothing();
	}

	// ---- Small helpers --------------------------------------------------------------------

	private static boolean containsAny(String text, String... words) {
		for (String word : words) {
			if (text.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsDigit(String value) {
		return value.chars().anyMatch(Character::isDigit);
	}

	private static int toInt(String digits) {
		return Integer.parseInt(digits);
	}

	private static String shorten(String value) {
		return value.length() > 120 ? value.substring(0, 120).strip() : value;
	}
}
