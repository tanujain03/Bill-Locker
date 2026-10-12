package project.bill_locker.warranty;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/** The warranties page: every product on your saved bills and its warranty status. */
@RestController
@RequestMapping("/api/warranties")
class WarrantyController {

	private final WarrantyService warranties;

	WarrantyController(WarrantyService warranties) {
		this.warranties = warranties;
	}

	@GetMapping
	WarrantyList list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) WarrantyStatus status,
			@RequestParam(required = false) String q) {
		return warranties.list(CurrentUser.id(jwt), status, q);
	}
}
