package project.bill_locker.gmail;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.common.ApiException;
import project.bill_locker.gmail.GmailViews.ConnectResponse;
import project.bill_locker.gmail.GmailViews.EmailView;
import project.bill_locker.gmail.GmailViews.FileIdsRequest;
import project.bill_locker.gmail.GmailViews.GmailEmailView;
import project.bill_locker.gmail.GmailViews.GmailFileView;
import project.bill_locker.gmail.GmailViews.GmailAccountView;
import project.bill_locker.gmail.GmailViews.GmailOverview;
import project.bill_locker.gmail.GmailViews.ScanRequest;
import project.bill_locker.security.CurrentUser;

/** HTTP ↔ Java for Gmail import. Everything needs a login token except Google's callback. */
@RestController
@RequestMapping("/api/integrations/gmail")
public class GmailController {

	private static final String COOKIE = "gmail_connect";

	private final GmailService gmail;
	private final GmailConnectService connectService;

	public GmailController(GmailService gmail, GmailConnectService connectService) {
		this.gmail = gmail;
		this.connectService = connectService;
	}

	@GetMapping
	public GmailOverview overview(@AuthenticationPrincipal Jwt jwt) {
		return gmail.overview(CurrentUser.id(jwt));
	}

	@PostMapping("/connect")
	public ResponseEntity<ConnectResponse> connect(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
		gmail.requireConfigured();
		String nonce = connectService.newBrowserNonce();
		ConnectResponse response = connectService.connect(CurrentUser.id(jwt), nonce);
		// Ties the flow to this browser: the callback only works with this cookie (HttpOnly: scripts cannot read it).
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(nonce, 600, request).toString()).body(response);
	}

	private static ResponseCookie cookie(String value, long maxAgeSeconds, HttpServletRequest request) {
		return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(request.isSecure()).sameSite("Lax")
				.path("/api/integrations/gmail").maxAge(maxAgeSeconds).build();
	}

	/** Public: it is the browser coming back from Google. Always a redirect to the Gmail page. */
	@GetMapping("/callback")
	public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error,
			@CookieValue(name = COOKIE, required = false) String browserNonce, HttpServletRequest request) {
		String redirect = connectService.callback(code, state, error, browserNonce);
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect))
				.header(HttpHeaders.SET_COOKIE, cookie("", 0, request).toString()).build(); // one use: clear it
	}

	@PostMapping("/accounts/{id}/scan")
	public GmailAccountView scan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody ScanRequest request) {
		gmail.requireConfigured();
		return gmail.queueScan(CurrentUser.id(jwt), id, request.range());
	}

	@GetMapping("/emails")
	public List<GmailEmailView> emails(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "TO_REVIEW") String view) {
		EmailView parsed;
		try {
			parsed = EmailView.valueOf(view);
		} catch (IllegalArgumentException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VIEW", "Unknown view.");
		}
		return gmail.emails(CurrentUser.id(jwt), parsed);
	}

	@PostMapping("/files/import")
	public List<GmailFileView> importFiles(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FileIdsRequest request) {
		gmail.requireConfigured();
		return gmail.importFiles(CurrentUser.id(jwt), request.fileIds());
	}

	@PostMapping("/files/ignore")
	public List<GmailFileView> ignoreFiles(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FileIdsRequest request) {
		gmail.requireConfigured();
		return gmail.ignoreFiles(CurrentUser.id(jwt), request.fileIds());
	}

	@PostMapping("/files/restore")
	public List<GmailFileView> restoreFiles(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FileIdsRequest request) {
		gmail.requireConfigured();
		return gmail.restoreFiles(CurrentUser.id(jwt), request.fileIds());
	}

	@DeleteMapping("/accounts/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disconnect(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		gmail.requireConfigured();
		connectService.disconnect(CurrentUser.id(jwt), id);
	}
}
