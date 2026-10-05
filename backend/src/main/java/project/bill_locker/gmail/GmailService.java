package project.bill_locker.gmail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.DocumentRepository;
import project.bill_locker.document.DocumentRepository.GmailDocumentLink;
import project.bill_locker.document.DocumentService;
import project.bill_locker.document.DocumentSummary;
import project.bill_locker.gmail.GmailResponses.ConnectResponse;
import project.bill_locker.gmail.GmailResponses.ConnectionResponse;
import project.bill_locker.gmail.GmailResponses.ImportResponse;
import project.bill_locker.gmail.GmailResponses.MessageResponse;
import project.bill_locker.gmail.GoogleApi.Email;
import project.bill_locker.gmail.GoogleApi.GoogleApiException;
import project.bill_locker.gmail.GoogleApi.Tokens;
import project.bill_locker.notification.NotificationService;
import project.bill_locker.user.User;
import project.bill_locker.user.UserService;

/**
 * Gmail import (docs/api-contract.md §13): connecting through Google's consent page,
 * the shortlist of bill emails, importing their attachments, and disconnecting. The
 * inbox scan itself runs in the background ({@link GmailScanWorker}).
 */
@Service
public class GmailService {

	/** Emails at least this likely to hold a bill count as "bills found" (the app's "Import all" uses 0.6 too). */
	static final double LIKELY_BILL = 0.6;
	private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
	private static final Set<String> IMPORTABLE_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp");
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Logger log = LoggerFactory.getLogger(GmailService.class);

	private final GmailProperties properties;
	private final GoogleApi google;
	private final TokenCipher cipher;
	private final GmailConnectionRepository connections;
	private final GmailOAuthStateRepository signIns;
	private final GmailMessageRepository messages;
	private final EmailClassifier classifier;
	private final UserService userService;
	private final DocumentService documentService;
	private final DocumentRepository documents;
	private final NotificationService notificationService;

	public GmailService(GmailProperties properties, GoogleApi google, TokenCipher cipher,
			GmailConnectionRepository connections, GmailOAuthStateRepository signIns, GmailMessageRepository messages,
			EmailClassifier classifier, UserService userService, DocumentService documentService,
			DocumentRepository documents, NotificationService notificationService) {
		this.properties = properties;
		this.google = google;
		this.cipher = cipher;
		this.connections = connections;
		this.signIns = signIns;
		this.messages = messages;
		this.classifier = classifier;
		this.userService = userService;
		this.documentService = documentService;
		this.documents = documents;
		this.notificationService = notificationService;
	}

	/** At start-up, the log says whether Gmail import can work and, if not, exactly what is missing. */
	@EventListener(ApplicationReadyEvent.class)
	public void reportSetup() {
		if (properties.isConfigured()) {
			log.info("Gmail import is set up. Google sends the browser back to {}, then to {}",
					properties.redirectUri(), properties.frontendUrl());
		}
		else {
			log.warn("Gmail import is off: {} (in backend/.env; see docs/step-7-codes-and-gmail.md §4)",
					String.join(", ", properties.problems()));
		}
	}

	@Transactional(readOnly = true)
	public ConnectionResponse connection(UUID userId) {
		return connections.findByUserId(userId).map(ConnectionResponse::from).orElse(ConnectionResponse.NOT_CONNECTED);
	}

	/**
	 * Connecting, step 1: remember a random "state" for this user, then send the browser to
	 * Google. PKCE: Google only gets the hash of a secret (the challenge); finishing needs
	 * the secret itself (the verifier), so a stolen one-time code is useless on its own.
	 */
	@Transactional
	public ConnectResponse startConnect(UUID userId) {
		if (!properties.isConfigured()) {
			log.warn("Gmail connect refused: {}", String.join(", ", properties.problems()));
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GMAIL_NOT_CONFIGURED",
					"Gmail import isn't set up on this server yet. The backend log says what is missing.");
		}
		signIns.deleteByExpiresAtBefore(Instant.now()); // tidy up sign-ins that were never finished
		String state = randomToken();
		String verifier = randomToken();
		signIns.save(new GmailOAuthState(state, userService.findUser(userId), verifier));
		return new ConnectResponse(google.authorizationUrl(state, challengeFor(verifier)));
	}

	/**
	 * Connecting, step 2: Google sends the browser back with a one-time code. This request
	 * has no login token, so the state says which user it is. Returns the app page to go to
	 * next: /gmail?status=connected, or /gmail?status=error&reason=…
	 */
	@Transactional
	public String finishConnect(String code, String state, String error) {
		GmailOAuthState signIn = state == null ? null : signIns.findById(state).orElse(null);
		if (signIn == null || signIn.isExpired()) {
			return appPage("error", "The Gmail sign-in expired. Please try again.");
		}
		signIns.delete(signIn); // a state works only once
		if (error != null || code == null) {
			return appPage("error", "Access to Gmail was not given.");
		}
		try {
			Tokens tokens = google.exchangeCode(code, signIn.getCodeVerifier());
			if (tokens.refreshToken() == null) {
				return appPage("error", "Google did not give lasting access. Please try again.");
			}
			String email = google.emailAddress(tokens.accessToken());
			User user = signIn.getUser();
			GmailConnection connection = connections.findByUserId(user.getId()).orElseGet(() -> new GmailConnection(user));
			connection.connect(email, cipher.encrypt(tokens.refreshToken())); // also starts the first scan
			connections.save(connection);
			return appPage("connected", null);
		}
		catch (GoogleApiException ex) {
			log.warn("Connecting Gmail failed: {}", ex.getMessage());
			return appPage("error", explain(ex));
		}
	}

	/** Google's answer turned into something to act on; shown on the Gmail page after a failed connect. */
	static String explain(GoogleApiException ex) {
		String answer = String.valueOf(ex.getMessage());
		if (answer.contains("SERVICE_DISABLED") || answer.contains("accessNotConfigured")
				|| answer.contains("has not been used in project")) {
			return "The Gmail API is not enabled in the Google Cloud project. Enable it, wait a minute, then connect again.";
		}
		if (answer.contains("invalid_client")) {
			return "Google did not accept the client ID or secret. Check GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET.";
		}
		if (answer.contains("redirect_uri_mismatch")) {
			return "The redirect URI is not listed for this OAuth client in the Google Cloud console.";
		}
		if (answer.contains("invalid_grant")) {
			return "The Google sign-in expired or was already used. Please connect again.";
		}
		return "Google could not confirm the connection. Please try again.";
	}

	/** "Scan now": the background scanner picks it up within a few seconds. */
	@Transactional
	public ConnectionResponse requestScan(UUID userId) {
		GmailConnection connection = connectionOf(userId);
		if (connection.getSyncStatus() != GmailSyncStatus.SYNCING) {
			connection.startSync();
		}
		return ConnectionResponse.from(connection);
	}

	@Transactional
	public ConnectionResponse updateSettings(UUID userId, boolean autoSync) {
		GmailConnection connection = connectionOf(userId);
		connection.setAutoSync(autoSync);
		return ConnectionResponse.from(connection);
	}

	/** The shortlist, newest email first, with the documents imported from each. */
	@Transactional(readOnly = true)
	public List<MessageResponse> messages(UUID userId) {
		Map<UUID, List<UUID>> imported = importedDocuments(userId);
		return messages.findByUserIdOrderByReceivedAtDesc(userId).stream()
				.map(message -> MessageResponse.from(message, imported.getOrDefault(message.getId(), List.of())))
				.toList();
	}

	/**
	 * Downloads the chosen emails' attachments and stores them as documents (source GMAIL).
	 * The background reader then reads them like uploads, and the user still reviews each.
	 */
	@Transactional
	public ImportResponse importMessages(UUID userId, List<UUID> messageIds) {
		if (messageIds == null || messageIds.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose at least one email.",
					Map.of("messageIds", "Choose at least one email"));
		}
		GmailConnection connection = connectionOf(userId);
		List<GmailMessage> chosen = messages.findByIdInAndUserId(messageIds, userId);
		if (chosen.size() != new HashSet<>(messageIds).size()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "GMAIL_MESSAGE_NOT_FOUND",
					"Some of the selected emails were not found.");
		}
		List<DocumentSummary> imported = new ArrayList<>();
		try {
			String accessToken = google.accessToken(cipher.decrypt(connection.getRefreshTokenCiphertext()));
			for (GmailMessage message : chosen) {
				if (message.getStatus() == GmailMessageStatus.IMPORTED) {
					continue;
				}
				for (GmailAttachment file : message.getAttachments()) {
					byte[] content = google.attachment(accessToken, message.getGmailMessageId(), file.attachmentId());
					documentService.importFromGmail(message, file.fileName(), content).ifPresent(imported::add);
				}
				message.markImported();
			}
		}
		catch (GoogleApiException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "GMAIL_UNAVAILABLE", ex.isAccessRevoked()
					? "Gmail access was removed. Disconnect Gmail and connect it again."
					: "Gmail could not be reached. Please try again.");
		}
		return new ImportResponse(imported);
	}

	@Transactional
	public MessageResponse ignore(UUID userId, UUID messageId) {
		GmailMessage message = messages.findByIdAndUserId(messageId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GMAIL_MESSAGE_NOT_FOUND", "The email was not found."));
		message.ignore();
		return MessageResponse.from(message, importedDocuments(userId).getOrDefault(message.getId(), List.of()));
	}

	/**
	 * Withdraws Bill Locker's access at Google, then deletes our copy of the token and the
	 * shortlist. Imported documents stay in the locker.
	 */
	@Transactional
	public void disconnect(UUID userId) {
		connections.findByUserId(userId).ifPresent(connection -> {
			try {
				google.revoke(cipher.decrypt(connection.getRefreshTokenCiphertext()));
			}
			catch (RuntimeException alreadyGoneOrUnreachable) {
				// Our copy of the token is deleted below either way.
			}
			messages.deleteByUserId(userId); // documents imported from them keep existing (gmail_message_id → null)
			connections.delete(connection);
		});
	}

	// ---- Used by GmailScanWorker: short transactions around the slow Gmail calls ---------

	/** What the scanner needs for one waiting scan. */
	public record ScanJob(UUID userId, String refreshToken, Set<String> knownMessageIds) {
	}

	@Transactional
	public List<ScanJob> pendingScans() {
		List<ScanJob> jobs = new ArrayList<>();
		for (GmailConnection connection : connections.findBySyncStatus(GmailSyncStatus.SYNCING)) {
			UUID userId = connection.getUser().getId();
			try {
				jobs.add(new ScanJob(userId, cipher.decrypt(connection.getRefreshTokenCiphertext()),
						messages.findGmailIds(userId)));
			}
			catch (IllegalStateException unreadableToken) { // e.g. GMAIL_TOKEN_KEY was changed
				connection.failSync("Please disconnect Gmail and connect it again.");
			}
		}
		return jobs;
	}

	/** Adds the new bill-like emails to the shortlist and tells the user if any look like bills. */
	@Transactional
	public void saveScan(UUID userId, List<Email> emails) {
		GmailConnection connection = connections.findByUserId(userId).orElse(null);
		if (connection == null) {
			return; // disconnected while the scan was running
		}
		int likelyBills = 0;
		for (Email email : emails) {
			List<GmailAttachment> files = email.attachments().stream().filter(GmailService::importable).toList();
			if (files.isEmpty() || messages.existsByUserIdAndGmailMessageId(userId, email.id())) {
				continue; // nothing to import, or already on the shortlist
			}
			EmailClassifier.Verdict verdict = classifier.classify(email.subject(), email.snippet(), files);
			messages.save(new GmailMessage(connection.getUser(), email, files, verdict));
			if (verdict.confidence() >= LIKELY_BILL) {
				likelyBills++;
			}
		}
		connection.finishSync();
		if (likelyBills > 0) {
			notificationService.gmailBillsFound(connection.getUser(), likelyBills);
		}
	}

	@Transactional
	public void failScan(UUID userId, String reason) {
		connections.findByUserId(userId).ifPresent(connection -> connection.failSync(reason));
	}

	/** The morning scan: every connection with auto-scan switched on gets one. */
	@Transactional
	public int startDailyScans() {
		List<GmailConnection> automatic = connections.findByAutoSyncTrue();
		automatic.forEach(GmailConnection::startSync);
		return automatic.size();
	}

	// ---- Helpers --------------------------------------------------------------------------

	private GmailConnection connectionOf(UUID userId) {
		return connections.findByUserId(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "GMAIL_NOT_CONNECTED", "Connect Gmail first."));
	}

	private Map<UUID, List<UUID>> importedDocuments(UUID userId) {
		return documents.findGmailLinks(userId).stream().collect(Collectors.groupingBy(GmailDocumentLink::getMessageId,
				Collectors.mapping(GmailDocumentLink::getDocumentId, Collectors.toList())));
	}

	/** PDFs and photos up to 10 MB, judged by name here; the real check happens on import. */
	static boolean importable(GmailAttachment file) {
		String name = file.fileName().toLowerCase(Locale.ROOT);
		String extension = name.substring(name.lastIndexOf('.') + 1);
		return IMPORTABLE_EXTENSIONS.contains(extension) && file.size() <= MAX_ATTACHMENT_BYTES;
	}

	/** 32 random bytes in URL-safe Base64 (43 characters). */
	private static String randomToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** PKCE "S256": the challenge is the SHA-256 hash of the verifier, in URL-safe Base64. */
	private static String challengeFor(String verifier) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String appPage(String status, String reason) {
		UriComponentsBuilder page = UriComponentsBuilder.fromUriString(properties.frontendUrl())
				.path("/gmail")
				.queryParam("status", status);
		if (reason != null) {
			page.queryParam("reason", reason);
		}
		return page.encode().toUriString();
	}
}
