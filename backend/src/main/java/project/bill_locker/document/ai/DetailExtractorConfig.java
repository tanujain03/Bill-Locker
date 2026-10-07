package project.bill_locker.document.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Chooses how documents are read, once at start-up:
 * GEMINI_API_KEY set → Gemini ({@link GeminiDetailExtractor});
 * not set → every "read" answers 503 AI_NOT_CONFIGURED and the user types the details in.
 */
@Configuration
class DetailExtractorConfig {

	private static final Logger log = LoggerFactory.getLogger(DetailExtractorConfig.class);

	@Bean
	DetailExtractor detailExtractor(@Value("${app.gemini.api-key:}") String apiKey,
			@Value("${app.gemini.model}") String modelSetting,
			@Value("${app.gemini.timeout}") Duration timeout) {
		if (apiKey.isBlank()) {
			log.warn("GEMINI_API_KEY is not set, so documents can't be read automatically. "
					+ "Get a free key at https://aistudio.google.com/apikey and put it in backend/.env.");
			return (file, contentType) -> {
				throw new ExtractionException(ExtractionException.NOT_CONFIGURED, "AI reading is not set up.");
			};
		}
		// "a,b,c": a first, b and c as backups when a is busy or out of quota.
		List<String> models = Arrays.stream(modelSetting.split(","))
				.map(String::trim)
				.filter(name -> !name.isEmpty())
				.toList();
		log.info("Documents are read with Gemini model(s) {}", models);

		// A bill with several pages can take Gemini a while; give up after the timeout.
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return new GeminiDetailExtractor(RestClient.builder().requestFactory(requestFactory).build(), apiKey, models);
	}
}
