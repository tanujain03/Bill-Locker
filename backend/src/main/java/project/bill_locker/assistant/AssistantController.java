package project.bill_locker.assistant;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/** The chat assistant: answers questions about the signed-in user's own data. */
@RestController
@RequestMapping("/api/assistant")
class AssistantController {

	private final AssistantService assistant;

	AssistantController(AssistantService assistant) {
		this.assistant = assistant;
	}

	@PostMapping("/chat")
	ChatResponse chat(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest request) {
		return assistant.chat(CurrentUser.id(jwt), request.messages());
	}
}
