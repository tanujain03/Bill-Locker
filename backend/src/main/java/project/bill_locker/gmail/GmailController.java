package project.bill_locker.gmail;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.gmail.GmailResponses.ConnectResponse;
import project.bill_locker.gmail.GmailResponses.ConnectionResponse;
import project.bill_locker.gmail.GmailResponses.ImportRequest;
import project.bill_locker.gmail.GmailResponses.ImportResponse;
import project.bill_locker.gmail.GmailResponses.MessageResponse;
import project.bill_locker.security.CurrentUser;

/**
 * Gmail import endpoints (docs/api-contract.md §13). All need a login token, except the
 * callback: Google's redirect can't carry one (SecurityConfig lets it through).
 */
@RestController
@RequestMapping("/api/integrations/gmail")
public class GmailController {

	/** Body of {@code PUT /settings}. */
	public record SettingsRequest(@NotNull(message = "Must be true or false") Boolean autoSync) {
	}

	private final GmailService gmailService;

	public GmailController(GmailService gmailService) {
		this.gmailService = gmailService;
	}

	/** {@code GET /api/integrations/gmail} — the connection (the app asks every 1.5 s while scanning). */
	@GetMapping
	public ConnectionResponse connection(@AuthenticationPrincipal Jwt jwt) {
		return gmailService.connection(CurrentUser.id(jwt));
	}

	/** {@code POST /api/integrations/gmail/connect} — where to send the browser to give access. */
	@PostMapping("/connect")
	public ConnectResponse connect(@AuthenticationPrincipal Jwt jwt) {
		return gmailService.startConnect(CurrentUser.id(jwt));
	}

	/** {@code GET /api/integrations/gmail/callback} — Google sends the browser here; we send it on to the app. */
	@GetMapping("/callback")
	public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error) {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(gmailService.finishConnect(code, state, error)))
				.build();
	}

	/** {@code POST /api/integrations/gmail/sync} — scan now. */
	@PostMapping("/sync")
	public ConnectionResponse sync(@AuthenticationPrincipal Jwt jwt) {
		return gmailService.requestScan(CurrentUser.id(jwt));
	}

	/** {@code PUT /api/integrations/gmail/settings} — {@code { "autoSync": true }}. */
	@PutMapping("/settings")
	public ConnectionResponse settings(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SettingsRequest request) {
		return gmailService.updateSettings(CurrentUser.id(jwt), request.autoSync());
	}

	/** {@code GET /api/integrations/gmail/messages} — the shortlist of bill emails. */
	@GetMapping("/messages")
	public List<MessageResponse> messages(@AuthenticationPrincipal Jwt jwt) {
		return gmailService.messages(CurrentUser.id(jwt));
	}

	/** {@code POST /api/integrations/gmail/import} — {@code { "messageIds": ["…"] }}. */
	@PostMapping("/import")
	public ImportResponse importMessages(@AuthenticationPrincipal Jwt jwt, @RequestBody ImportRequest request) {
		return gmailService.importMessages(CurrentUser.id(jwt), request.messageIds());
	}

	/** {@code POST /api/integrations/gmail/messages/{id}/ignore} */
	@PostMapping("/messages/{id}/ignore")
	public MessageResponse ignore(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return gmailService.ignore(CurrentUser.id(jwt), id);
	}

	/** {@code DELETE /api/integrations/gmail} — disconnect; imported documents stay. */
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disconnect(@AuthenticationPrincipal Jwt jwt) {
		gmailService.disconnect(CurrentUser.id(jwt));
	}
}
