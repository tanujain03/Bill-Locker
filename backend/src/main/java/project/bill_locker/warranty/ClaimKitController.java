package project.bill_locker.warranty;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/** The claim kit page: one product of one bill, by the bill's id and the product's position. */
@RestController
class ClaimKitController {

	private final ClaimKitService claimKits;

	ClaimKitController(ClaimKitService claimKits) {
		this.claimKits = claimKits;
	}

	@GetMapping("/api/documents/{id}/items/{position}/claim-kit")
	ClaimKit kit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable int position) {
		return claimKits.kit(CurrentUser.id(jwt), id, position);
	}
}
