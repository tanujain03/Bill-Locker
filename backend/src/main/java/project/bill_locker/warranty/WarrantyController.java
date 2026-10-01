package project.bill_locker.warranty;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/** Warranty endpoint (docs/api-contract.md §6), used by the Warranties page. */
@RestController
@RequestMapping("/api/warranties")
public class WarrantyController {

	private final WarrantyService warrantyService;

	public WarrantyController(WarrantyService warrantyService) {
		this.warrantyService = warrantyService;
	}

	/** {@code GET /api/warranties?status=} — soonest expiry first, unknown ones last. */
	@GetMapping
	public List<WarrantyResponse> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) WarrantyStatus status) {
		return warrantyService.list(CurrentUser.id(jwt), status);
	}
}
