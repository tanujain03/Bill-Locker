package project.bill_locker.gmail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Makes the app.gmail.* settings available as {@link GmailProperties}. */
@Configuration
@EnableConfigurationProperties(GmailProperties.class)
class GmailConfig {
}
