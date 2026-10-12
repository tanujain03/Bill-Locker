package project.bill_locker.gmail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import project.bill_locker.gmail.GmailViews.ConnectResponse;
import project.bill_locker.gmail.GmailViews.ScanRange;
import project.bill_locker.gmail.GoogleApi.GoogleApiException;
import project.bill_locker.gmail.GoogleApi.Tokens;
import project.bill_locker.user.User;
import project.bill_locker.user.UserRepository;

/**
 * Connecting and disconnecting a Gmail address. The Google calls (slow, may fail) never
 * run inside a database transaction, so each DB step uses its own short one.
 */
@Service
public class GmailConnectService {

	private static final Logger log = LoggerFactory.getLogger(GmailConnectService.class);
	private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);

	private final SecureRandom random = new SecureRandom();
	private final GoogleApi google;
	private final TokenCipher cipher;
	private final GmailService gmail;
	private final GmailAccountRepository accounts;
	private final GmailConnectStateRepository states;
	private final UserRepository users;
	private final TransactionTemplate transaction;
	private final String gmailPage;

	public GmailConnectService(GoogleApi google, TokenCipher cipher, GmailService gmail, GmailAccountRepository accounts,
			GmailConnectStateRepository states, UserRepository users, PlatformTransactionManager transactionManager,
			@Value("${app.frontend-url}") String frontendUrl) {
		this.google = google;
		this.cipher = cipher;
		this.gmail = gmail;
		this.accounts = accounts;
		this.states = states;
		this.users = users;
		this.transaction = new TransactionTemplate(transactionManager);
		this.gmailPage = frontendUrl.replaceAll("/+$", "") + "/gmail"; // no trailing slash
	}

	/** Step 1: remember who clicked Connect, then send them to Google. */
	public ConnectResponse connect(UUID userId, String browserNonce) {
		String state = randomToken();
		String verifier = randomToken(); // PKCE: proves the code exchange comes from the same server
		String challenge = base64Url(sha256Bytes(verifier));

		transaction.executeWithoutResult(status -> {
			User user = users.findById(userId).orElseThrow();
			states.deleteByUserIdAndExpiresAtBefore(userId, Instant.now());
			states.save(new GmailConnectState(user, sha256Hex(state), verifier, sha256Hex(browserNonce),
					Instant.now().plus(STATE_LIFETIME)));
		});
		return new ConnectResponse(google.authorizationUrl(state, challenge));
	}

	/** Goes in a cookie, so only the browser that clicked Connect can finish the flow. */
	public String newBrowserNonce() {
		return randomToken();
	}

	/**
	 * Step 2: Google sends the browser back here (no login token, so the one-time state
	 * says who it is). Always answers with the page to redirect to, never with details.
	 */
	public String callback(String code, String state, String error, String browserNonce) {
		try {
			return finishConnect(code, state, error, browserNonce);
		} catch (RuntimeException e) {
			// Must always redirect, never a 500 (e.g. two callbacks racing). No details: they could hold secrets.
			log.warn("Gmail connect failed unexpectedly: {}", e.getClass().getSimpleName());
			return gmailPage + "?error=failed";
		}
	}

	private String finishConnect(String code, String state, String error, String browserNonce) {
		if (!gmail.isConfigured()) {
			return gmailPage + "?error=failed";
		}
		record Pending(UUID userId, String verifier, String browserHash, boolean expired) {
		}
		// One use: the row is deleted whatever happens next, and only the caller whose delete counted wins.
		Pending claimed = state == null ? null : transaction.execute(status -> {
			String hash = sha256Hex(state);
			Pending found = states.findByStateHash(hash)
					.map(row -> new Pending(row.getUser().getId(), row.getCodeVerifier(), row.getBrowserHash(),
							row.isExpired(Instant.now())))
					.orElse(null);
			if (found == null || states.consume(hash) == 0 || found.expired()) {
				return null;
			}
			return found;
		});
		// A missing or different cookie means another browser: a victim must not link their mailbox to someone else.
		boolean sameBrowser = claimed != null && browserNonce != null
				&& sha256Hex(browserNonce).equals(claimed.browserHash());
		Pending pending = sameBrowser ? claimed : null;
		if (pending == null) {
			return gmailPage + "?error=expired";
		}
		if (error != null || code == null) {
			return gmailPage + "?error=denied";
		}

		String address;
		String refreshToken;
		try {
			Tokens tokens = google.exchangeCode(code, pending.verifier());
			address = google.emailAddress(tokens.accessToken());
			refreshToken = tokens.refreshToken();
		} catch (GoogleApiException e) {
			return gmailPage + "?error=failed";
		}
		if (refreshToken == null || address == null || address.isBlank()) {
			return gmailPage + "?error=failed";
		}

		byte[] encrypted = cipher.encrypt(refreshToken);
		LocalDate since = ScanRange.ONE_YEAR.since(LocalDate.now());
		try {
			saveAccount(pending.userId(), address, encrypted, since);
		} catch (DataIntegrityViolationException twoAtOnce) {
			// Two first-time connects of the same address raced on the unique key: the row exists now, so update it.
			saveAccount(pending.userId(), address, encrypted, since);
		}
		return gmailPage + "?connected=" + URLEncoder.encode(address, StandardCharsets.UTF_8);
	}

	private void saveAccount(UUID userId, String address, byte[] encrypted, LocalDate since) {
		transaction.executeWithoutResult(status -> {
			// The same address again (e.g. after access was withdrawn) just gets a new token.
			GmailAccount account = accounts.findByUserIdAndEmail(userId, address)
					.orElseGet(() -> new GmailAccount(users.findById(userId).orElseThrow(), address, encrypted));
			account.updateToken(encrypted);
			account.queueScan(since);
			accounts.saveAndFlush(account);
		});
	}

	/** Revokes at Google (best effort), then deletes the account with its emails and files. Documents stay. */
	public void disconnect(UUID userId, UUID accountId) {
		byte[] encrypted = transaction.execute(status -> gmail.findAccount(userId, accountId).getRefreshTokenCiphertext());
		try {
			google.revoke(cipher.decrypt(encrypted));
		} catch (GoogleApiException | IllegalStateException ignored) {
			// The user may have revoked already (Google then answers 400): we still delete our copy.
		}
		transaction.executeWithoutResult(status -> accounts.deleteById(accountId));
	}

	private String randomToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return base64Url(bytes);
	}

	private static String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static byte[] sha256Bytes(String text) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}

	private static String sha256Hex(String text) {
		return HexFormat.of().formatHex(sha256Bytes(text));
	}
}
