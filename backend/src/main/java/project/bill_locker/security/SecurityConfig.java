package project.bill_locker.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	static final String ISSUER = "bill-locker";

	private static final List<String> PUBLIC_ENDPOINTS = List.of(
			"/api/auth/register",
			"/api/auth/login",
			"/api/auth/forgot-password",
			"/api/auth/reset-password");

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthenticationEntryPoint entryPoint) throws Exception {
		http
				// The token travels in a header, not in a cookie, so CSRF attacks don't apply.
				.csrf(AbstractHttpConfigurer::disable)
				// No server-side session: every request brings its own token.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS.toArray(String[]::new)).permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				// Read "Authorization: Bearer <token>" and verify it with jwtDecoder() below.
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(Customizer.withDefaults())
						.bearerTokenResolver(bearerTokenResolver())
						.authenticationEntryPoint(entryPoint))
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint));
		return http.build();
	}

	/** Passwords are stored as BCrypt hashes, never as the password itself. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** The secret that signs tokens (HMAC-SHA256). Only this server knows it. */
	@Bean
	SecretKey jwtSigningKey(JwtProperties properties) {
		String secret = properties.secret();
		if (secret == null || secret.isBlank()) {
			log.warn("JWT_SECRET is not set, so a random secret is used and everyone must log in again after a "
					+ "restart. Set JWT_SECRET in backend/.env (see .env.example).");
			byte[] random = new byte[32];
			new SecureRandom().nextBytes(random);
			return new SecretKeySpec(random, "HmacSHA256");
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET must be at least 32 characters long.");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
	}

	/** Accepts a token only if our secret signed it, it has not expired, and we issued it. */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

	/**
	 * Reads the token from the Authorization header, except on the public endpoints:
	 * an old, expired token left in the browser must never stop someone from signing in.
	 */
	private static BearerTokenResolver bearerTokenResolver() {
		DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
		return request -> PUBLIC_ENDPOINTS.contains(request.getRequestURI()) ? null : headerResolver.resolve(request);
	}
}
