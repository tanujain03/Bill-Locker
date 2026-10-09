package project.bill_locker.document.ai;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import project.bill_locker.document.DocumentDetails;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a bill with Google Gemini (free tier), using its REST API directly.
 *
 * <p>One request carries three things: our instructions (the prompt), the file
 * itself (base64, as "inline_data"), and a JSON schema. The schema makes Gemini
 * answer with exactly our fields as JSON instead of free text, so we can read
 * the answer with {@link GeminiAnswerParser}.
 */
public class GeminiDetailExtractor implements DetailExtractor {

	private static final Logger log = LoggerFactory.getLogger(GeminiDetailExtractor.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();

	public static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

	private static final String PROMPT = """
			You read shopping documents: invoices, warranty cards and receipts.
			Extract the details of the attached document into the JSON schema.
			Rules:
			- Copy values exactly as printed. Never guess or invent a value; use null when it is not printed.
			- documentType: INVOICE (a tax invoice for buying products, e.g. electronics or appliances),
			  WARRANTY_CARD, RECEIPT (a bill or receipt for a service or everyday spending: rides, food, groceries,
			  fuel, travel, electricity/water/gas, phone/internet bills, recharges, subscriptions) or OTHER.
			- category: only for a RECEIPT, what it was for: TRAVEL (cabs, rides, trains, flights, hotels), FOOD
			  (restaurants, food delivery), GROCERIES, FUEL, UTILITIES (electricity, water, gas), PHONE_INTERNET (mobile,
			  broadband, recharges, DTH), SHOPPING, HEALTH (pharmacy, doctor, lab) or OTHER. null for other document types.
			- Add one entry to "items" for every product line on the document (not for taxes, discounts or delivery).
			- Dates as YYYY-MM-DD. Amounts as plain numbers without currency symbols or thousands separators.
			- unitPrice is the price of one unit before tax; taxAmount is the total tax (GST/VAT); totalAmount is the final amount paid.
			- warrantyPeriodMonths: the warranty length in months (1 year = 12). warrantyStartDate is usually the purchase date.
			- serialNumber: the serial number or IMEI of that exact unit.
			- brand: the manufacturer's brand of the product (e.g. Samsung, Noise, boAt), from the product name or the
			  document. null for services such as rides, food, delivery or bills for utilities.
			- warrantyRegistrationUrl: a web link printed on the document for registering this product's warranty
			  (or the product), copied exactly. null when none is printed.
			""";

	/** Added to the prompt when QR codes were found: their links, decoded exactly. */
	private static final String QR_HINT = """
			The document's QR codes contain these links (decoded exactly): %s
			If one of them is for warranty or product registration (look at the text printed next to the QR code),
			use it as warrantyRegistrationUrl for the products it belongs to.
			""";

	private final RestClient restClient;
	private final String apiKey;
	/** Tried in this order; the next one only when the one before is busy or out of quota. */
	private final List<String> models;

	public GeminiDetailExtractor(RestClient restClient, String apiKey, List<String> models) {
		if (models.isEmpty()) {
			throw new IllegalArgumentException("Set at least one GEMINI_MODEL");
		}
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.models = List.copyOf(models);
	}

	@Override
	public DocumentDetails extract(byte[] file, String contentType) {
		return extract(file, contentType, List.of());
	}

	@Override
	public DocumentDetails extract(byte[] file, String contentType, List<String> qrLinks) {
		String prompt = qrLinks.isEmpty() ? PROMPT : PROMPT + QR_HINT.formatted(String.join(" , ", qrLinks));
		String body = JSON.writeValueAsString(requestBody(file, contentType, prompt));
		int lastStatus = 0;
		for (String model : models) {
			String reply;
			try {
				reply = restClient.post()
						.uri(URL, model)
						.header("x-goog-api-key", apiKey) // in a header, so the key never shows up in logged URLs
						.contentType(MediaType.APPLICATION_JSON)
						.body(body)
						.retrieve()
						.body(String.class);
			} catch (RestClientResponseException e) {
				lastStatus = e.getStatusCode().value();
				log.warn("Gemini model {} answered HTTP {}: {}", model, lastStatus, e.getResponseBodyAsString());
				// Busy (503) and quota (429) are per model, so another model may still work.
				// Anything else (bad key, broken file) would fail on every model: stop here.
				if (lastStatus == 503 || lastStatus == 429) {
					continue;
				}
				throw new ExtractionException(ExtractionException.FAILED, messageFor(lastStatus));
			} catch (RestClientException e) { // no connection, timeout…
				log.warn("Could not reach Gemini: {}", e.getMessage());
				throw new ExtractionException(ExtractionException.FAILED,
						"Gemini could not read this document right now. Please try again.");
			}
			return GeminiAnswerParser.parse(answerText(reply));
		}
		// Every model was busy or out of quota: report what the last one said.
		throw new ExtractionException(ExtractionException.FAILED, messageFor(lastStatus));
	}

	private static String messageFor(int status) {
		if (status == 429) {
			return "The free Gemini limit is used up for now. Wait a minute (or until tomorrow) and try again.";
		}
		if (status == 400) { // INVALID_ARGUMENT: usually a damaged or password-protected file
			return "Gemini couldn't open this file. Try a different scan or photo, or type the details in.";
		}
		if (status == 401 || status == 403 || status == 404) {
			return "Gemini refused the request. Check GEMINI_API_KEY and GEMINI_MODEL in backend/.env.";
		}
		if (status == 503) { // "This model is currently experiencing high demand"
			return "Gemini is busy right now. Please try again in a minute (or add another model to GEMINI_MODEL).";
		}
		return "Gemini could not read this document right now. Please try again.";
	}

	/** Gemini's reply: {"candidates": [{"content": {"parts": [{"text": "<our JSON>"}]}}]}. */
	private static String answerText(String reply) {
		try {
			JsonNode text = JSON.readTree(reply == null ? "" : reply)
					.path("candidates").path(0).path("content").path("parts").path(0).path("text");
			if (text.isString()) {
				return text.asString();
			}
		} catch (JacksonException e) {
			// falls through to "no answer"
		}
		log.warn("Gemini returned no answer: {}", reply);
		throw new ExtractionException(ExtractionException.FAILED, "Gemini returned no answer for this document.");
	}

	private static Map<String, Object> requestBody(byte[] file, String contentType, String prompt) {
		Map<String, Object> inlineData = Map.of(
				"mime_type", contentType,
				"data", Base64.getEncoder().encodeToString(file));
		return Map.of(
				"contents", List.of(Map.of("parts", List.of(
						Map.of("text", prompt),
						Map.of("inline_data", inlineData)))),
				"generationConfig", Map.of(
						"response_mime_type", "application/json",
						"response_schema", SCHEMA,
						"temperature", 0)); // 0 = no creativity: the same bill gives the same answer
	}

	// ---- The answer's shape (Gemini's schema format: types in capitals) ------------

	private static final Map<String, Object> SCHEMA = object(props(
			"documentType", Map.of("type", "STRING", "nullable", true,
					"enum", List.of("INVOICE", "WARRANTY_CARD", "RECEIPT", "OTHER")),
			"documentNumber", text("Invoice/receipt/warranty number printed on the document"),
			"sellerName", text("Legal name of the store or company that sold the item"),
			"sellerAddress", text("Seller's address"),
			"sellerContact", text("Seller's phone number or support email"),
			"buyerName", text("Name of the customer"),
			"buyerAddress", text("Customer's billing or shipping address"),
			"buyerEmail", text("Customer's email"),
			"purchaseDate", text("Date of purchase, YYYY-MM-DD"),
			"taxAmount", number("Total tax (GST/VAT/sales tax)"),
			"totalAmount", number("Final amount paid"),
			"category", Map.of("type", "STRING", "nullable", true, "description", "Only for RECEIPT: what it was for",
					"enum", List.of("TRAVEL", "FOOD", "GROCERIES", "FUEL", "UTILITIES", "PHONE_INTERNET", "SHOPPING",
							"HEALTH", "OTHER")),
			"items", Map.of("type", "ARRAY", "items", object(props(
					"productName", text("Name of the product"),
					"modelNumber", text("Manufacturer model number"),
					"serialNumber", text("Serial number or IMEI of this unit"),
					"unitPrice", number("Price of one unit before tax"),
					"warrantyPeriodMonths", Map.of("type", "INTEGER", "nullable", true,
							"description", "Warranty length in months"),
					"warrantyStartDate", text("Warranty start date, YYYY-MM-DD"),
					"warrantyEndDate", text("Warranty end date, YYYY-MM-DD"),
					"warrantyProvider", text("Who services the warranty, e.g. the manufacturer"),
					"brand", text("Manufacturer's brand of the product; null for services"),
					"warrantyRegistrationUrl", text("Link printed for registering the warranty"))))));

	private static Map<String, Object> object(Map<String, Object> properties) {
		return Map.of("type", "OBJECT", "properties", properties);
	}

	private static Map<String, Object> text(String description) {
		return Map.of("type", "STRING", "nullable", true, "description", description);
	}

	private static Map<String, Object> number(String description) {
		return Map.of("type", "NUMBER", "nullable", true, "description", description);
	}

	/** Like Map.of, but keeps the order (the order fields appear in Gemini's answer). */
	private static Map<String, Object> props(Object... keysAndValues) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			map.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return map;
	}
}
