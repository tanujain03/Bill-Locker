package project.bill_locker.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/** Dashboard endpoint (docs/api-contract.md §7). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	/** {@code GET /api/dashboard/summary} — totals, warranty counts, spending and recent activity. */
	@GetMapping("/summary")
	public DashboardSummary summary(@AuthenticationPrincipal Jwt jwt) {
		return dashboardService.summary(CurrentUser.id(jwt));
	}
}
