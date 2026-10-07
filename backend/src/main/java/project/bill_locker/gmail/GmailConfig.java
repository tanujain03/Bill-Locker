package project.bill_locker.gmail;

import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Binds the Gmail settings and says at start-up whether Gmail import is on. */
@Configuration
@EnableConfigurationProperties(GmailProperties.class)
class GmailConfig {

	private static final Logger log = LoggerFactory.getLogger(GmailConfig.class);

	private final GmailProperties properties;

	GmailConfig(GmailProperties properties) {
		this.properties = properties;
	}

	/** The real Google; tests replace it with a fake. Timeouts so a slow Google can't hang a worker. */
	@Bean
	GoogleApi googleApi(GmailProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(30));
		return new GoogleRestApi(RestClient.builder().requestFactory(requestFactory), properties);
	}

	// After start-up so the message is easy to find at the end of the log.
	@EventListener(ApplicationReadyEvent.class)
	void logStatus() {
		if (properties.isConfigured()) {
			log.info("Gmail import is on (redirect URI {})", properties.redirectUri());
		} else if (blank(properties.clientId())) {
			log.warn("Gmail import is off: GOOGLE_CLIENT_ID is not set");
		} else if (blank(properties.clientSecret())) {
			log.warn("Gmail import is off: GOOGLE_CLIENT_SECRET is not set");
		} else if (blank(properties.tokenKey())) {
			log.warn("Gmail import is off: GMAIL_TOKEN_KEY is not set");
		} else if (properties.keyBytes() == null) {
			log.warn("Gmail import is off: GMAIL_TOKEN_KEY must be 32 bytes in base64 (openssl rand -base64 32)");
		} else {
			log.warn("Gmail import is off: GMAIL_REDIRECT_URI is not set");
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
