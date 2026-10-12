package project.bill_locker.document.registration;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import project.bill_locker.document.ai.GeminiDetailExtractor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks Gemini to search Google for a brand's warranty registration page.
 *
 * <p>Same REST API as reading bills, plus the "google_search" tool: Gemini runs real
 * searches and answers from the results (not from memory, where it could invent an
 * address). We then take the links from its answer, prefer ones on the brand's own
 * domain, and keep the first one that really opens ({@link LinkChecker}).
 */
public class GeminiRegistrationFinder implements RegistrationFinder {

	private static final Logger log = LoggerFactory.getLogger(GeminiRegistrationFinder.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"'()\\[\\]]+");
	/** Gemini's own redirect links to search results: not the brand's page. */
	private static final String GOOGLE_REDIRECT_HOST = "vertexaisearch.cloud.google.com";
	private static final int MAX_LINKS_TRIED = 3;

	private static final String PROMPT = """
			Find the official warranty registration page of the brand "%s" (product: "%s") for customers in India.
			Search the web. Answer with only the full URL of that page on the brand's official website,
			not a shop, marketplace, forum or third-party site.
			If the brand has no registration page, give its official warranty or support page instead.
			If you can't find an official page, answer NONE.
			""";

	private final RestClient restClient;
	private final String apiKey;
	private final List<String> models;
	private final LinkChecker linkChecker;

	public GeminiRegistrationFinder(RestClient restClient, String apiKey, List<String> models, LinkChecker linkChecker) {
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.models = List.copyOf(models);
		this.linkChecker = linkChecker;
	}

	@Override
	public Optional<String> find(String brand, String productName) {
		String answer = ask(PROMPT.formatted(brand, productName == null ? brand : productName));
		if (answer == null) {
			return Optional.empty();
		}
		return candidates(answer, brand).stream()
				.limit(MAX_LINKS_TRIED)
				.map(linkChecker::opens)
				.flatMap(Optional::stream)
				.findFirst();
	}

	/** Gemini's answer text, or null when no model could answer (busy, quota, offline…). */
	private String ask(String prompt) {
		String body = JSON.writeValueAsString(Map.of(
				"contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
				"tools", List.of(Map.of("google_search", Map.of())),
				"generationConfig", Map.of("temperature", 0)));
		for (String model : models) {
			try {
				String reply = restClient.post()
						.uri(GeminiDetailExtractor.URL, model)
						.header("x-goog-api-key", apiKey)
						.contentType(MediaType.APPLICATION_JSON)
						.body(body)
						.retrieve()
						.body(String.class);
				return answerText(reply);
			} catch (RestClientResponseException e) {
				int status = e.getStatusCode().value();
				log.warn("Gemini model {} answered HTTP {} to the registration search", model, status);
				if (status != 503 && status != 429) {
					return null; // a bad key or request fails on every model
				}
			} catch (RestClientException e) {
				log.warn("Could not reach Gemini for the registration search: {}", e.getMessage());
				return null;
			}
		}
		return null;
	}

	/** All text parts of the first answer (a grounded answer can come in several parts). */
	private static String answerText(String reply) {
		try {
			StringBuilder text = new StringBuilder();
			for (JsonNode part : JSON.readTree(reply == null ? "" : reply)
					.path("candidates").path(0).path("content").path("parts")) {
				if (part.path("text").isString()) {
					text.append(part.path("text").asString()).append('\n');
				}
			}
			return text.isEmpty() ? null : text.toString();
		} catch (JacksonException e) {
			return null;
		}
	}

	/** The links in the answer, those on the brand's own domain first ("gonoise.com" for "Noise"). */
	static List<String> candidates(String answer, String brand) {
		Set<String> links = new LinkedHashSet<>();
		Matcher m = LINK.matcher(answer);
		while (m.find()) {
			String link = m.group().replaceAll("[.,;:!?*_`]+$", ""); // a full stop or markdown after the URL
			URI uri;
			try {
				uri = URI.create(link);
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(GOOGLE_REDIRECT_HOST)) {
				links.add(link);
			}
		}
		String brandWord = brand.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		List<String> sorted = new ArrayList<>(links);
		sorted.sort(Comparator.comparing((String link) -> !URI.create(link).getHost().toLowerCase(Locale.ROOT)
				.replace("-", "").contains(brandWord))); // false (= on the brand's domain) sorts first
		return sorted;
	}
}
