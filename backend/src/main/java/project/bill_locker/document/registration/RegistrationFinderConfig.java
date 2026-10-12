package project.bill_locker.document.registration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * GEMINI_API_KEY set → registration pages are searched with Gemini + Google Search.
 * Not set → nothing is found, and products get the plain Google search link instead.
 */
@Configuration
class RegistrationFinderConfig {

	@Bean
	RegistrationFinder registrationFinder(@Value("${app.gemini.api-key:}") String apiKey,
			@Value("${app.gemini.model}") String modelSetting,
			@Value("${app.gemini.timeout}") Duration timeout) {
		if (apiKey.isBlank()) {
			return (brand, productName) -> Optional.empty();
		}
		List<String> models = Arrays.stream(modelSetting.split(",")).map(String::trim).filter(m -> !m.isEmpty()).toList();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		requestFactory.setReadTimeout(timeout); // a search takes a few seconds longer than a plain answer
		return new GeminiRegistrationFinder(RestClient.builder().requestFactory(requestFactory).build(), apiKey, models,
				new LinkChecker());
	}
}
