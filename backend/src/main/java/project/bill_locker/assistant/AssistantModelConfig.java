package project.bill_locker.assistant;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Chooses the assistant's AI once at start-up, from the same settings as document reading:
 * GEMINI_API_KEY set → Gemini; not set → every question answers 503 AI_NOT_CONFIGURED.
 */
@Configuration
class AssistantModelConfig {

	@Bean
	AssistantModel assistantModel(@Value("${app.gemini.api-key:}") String apiKey,
			@Value("${app.gemini.model}") String modelSetting,
			@Value("${app.gemini.timeout}") Duration timeout) {
		if (apiKey.isBlank()) {
			return new AssistantModel() {
				@Override
				public Plan plan(List<ChatMessage> messages, LocalDate today, String failedSql, String error) {
					throw notConfigured();
				}

				@Override
				public String answer(List<ChatMessage> messages, String sql, List<Map<String, Object>> rows) {
					throw notConfigured();
				}
			};
		}
		// "a,b,c": a first, b and c as backups when a is busy or out of quota.
		List<String> models = Arrays.stream(modelSetting.split(","))
				.map(String::trim)
				.filter(name -> !name.isEmpty())
				.toList();
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return new GeminiAssistantModel(RestClient.builder().requestFactory(requestFactory).build(), apiKey, models);
	}

	private static AssistantException notConfigured() {
		return new AssistantException(AssistantException.NOT_CONFIGURED,
				"The assistant isn't set up (no GEMINI_API_KEY).");
	}
}
