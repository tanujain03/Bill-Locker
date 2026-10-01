package project.bill_locker.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code app.jwt.*} settings from application.properties.
 *
 * @param secret     text that signs the login tokens (JWT_SECRET, 32+ characters);
 *                   empty means a random secret for this run only
 * @param expiration how long a login stays valid, e.g. {@code 24h}
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
