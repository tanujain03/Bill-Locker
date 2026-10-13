package project.bill_locker.sample;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/** "Try with sample data" (dashboard, Settings): see, add or remove the demo bills. */
@RestController
@RequestMapping("/api/sample-data")
class SampleDataController {

	private final SampleDataService sampleData;

	SampleDataController(SampleDataService sampleData) {
		this.sampleData = sampleData;
	}

	@GetMapping
	SampleDataStatus status(@AuthenticationPrincipal Jwt jwt) {
		return sampleData.status(CurrentUser.id(jwt));
	}

	@PostMapping
	SampleDataStatus add(@AuthenticationPrincipal Jwt jwt) {
		return sampleData.add(CurrentUser.id(jwt));
	}

	@DeleteMapping
	SampleDataStatus remove(@AuthenticationPrincipal Jwt jwt) {
		sampleData.remove(CurrentUser.id(jwt));
		return new SampleDataStatus(0);
	}
}
