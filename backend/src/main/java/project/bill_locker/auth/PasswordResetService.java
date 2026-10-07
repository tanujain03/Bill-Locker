package project.bill_locker.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.user.User;
import project.bill_locker.user.UserRepository;

/**
 * "Forgot password" in two steps:
 * <ol>
 *   <li>{@link #requestReset}: make a random one-time token, save its hash, send the
 *       link {@code <frontend>/reset-password?token=<token>} to the account's email.</li>
 *   <li>{@link #resetPassword}: the reset page sends the token back with the new
 *       password; if the token is known and not expired, change the password.</li>
 * </ol>
 */
@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository users;
	private final PasswordResetTokenRepository tokens;
	private final PasswordEncoder passwordEncoder;
	private final ResetLinkSender linkSender;
	private final String frontendUrl;
	private final Duration expiration;

	public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
			PasswordEncoder passwordEncoder, ResetLinkSender linkSender,
			@Value("${app.frontend-url}") String frontendUrl,
			@Value("${app.password-reset.expiration}") Duration expiration) {
		this.users = users;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.linkSender = linkSender;
		this.frontendUrl = frontendUrl.replaceAll("/+$", ""); // no trailing slash
		this.expiration = expiration;
	}

	/**
	 * Sends a reset link if the email has an account, and does nothing otherwise.
	 * The caller always gets the same answer, so this can't be used to find out
	 * which emails are registered.
	 */
	@Transactional
	public void requestReset(String email) {
		users.findByEmail(User.normalizeEmail(email)).ifPresentOrElse(user -> {
			tokens.deleteByUser(user); // only the newest link works
			String token = newToken();
			tokens.save(new PasswordResetToken(user, sha256(token), Instant.now().plus(expiration)));
			linkSender.send(user.getEmail(), frontendUrl + "/reset-password?token=" + token);
		},
				// Only the server log says this; the page answers the same either way.
				() -> log.info("Password reset asked for {}, but no account has this email, so nothing was sent.", email));
	}

	@Transactional
	public void resetPassword(String token, String newPassword) {
		PasswordResetToken saved = tokens.findByTokenHash(sha256(token))
				.filter(t -> !t.isExpired(Instant.now()))
				.orElseThrow(PasswordResetService::invalidLink);
		User user = saved.getUser();
		user.changePasswordHash(passwordEncoder.encode(newPassword));
		tokens.deleteByUser(user); // the link is used up
	}

	/** 32 random bytes as URL-safe text (43 characters): impossible to guess. */
	private static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String sha256(String text) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Every Java has SHA-256", ex);
		}
	}

	/** Unknown, already used and expired links all get the same answer. */
	private static ApiException invalidLink() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
				"This reset link is invalid or has expired. Please ask for a new one.");
	}
}
