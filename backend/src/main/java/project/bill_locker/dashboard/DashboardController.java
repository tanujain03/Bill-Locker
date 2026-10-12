package project.bill_locker.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/** The signed-in start page's numbers, in one request. */
@RestController
class DashboardController {

	private final DashboardService dashboard;

	DashboardController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping("/api/dashboard")
	DashboardResponse summary(@AuthenticationPrincipal Jwt jwt) {
		return dashboard.summary(CurrentUser.id(jwt));
	}
}
