package project.bill_locker.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Creates the login token (a JWT) that the frontend sends with every request.
 *
 * <p>A JWT is three base64 parts separated by dots: header.claims.signature. The
 * claims say "this is user {id}, valid until {time}"; the signature, made with our
 * secret, lets us detect any change to them. The browser can read a token but
 * cannot forge one.
 */
@Service
public class TokenService {

	private final JwtEncoder jwtEncoder;
	private final Duration expiration;

	public TokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.expiration = properties.expiration() != null ? properties.expiration() : Duration.ofHours(24);
	}

	public IssuedToken issueFor(UUID userId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(expiration);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(SecurityConfig.ISSUER)
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	public record IssuedToken(String value, Instant expiresAt) {
	}
}
