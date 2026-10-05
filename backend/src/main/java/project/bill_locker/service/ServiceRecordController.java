package project.bill_locker.service;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
import project.bill_locker.security.CurrentUser;

/** Service record endpoints (docs/api-contract.md §9). */
@RestController
@RequestMapping("/api/service-records")
public class ServiceRecordController {

	private final ServiceRecordService serviceRecordService;

	public ServiceRecordController(ServiceRecordService serviceRecordService) {
		this.serviceRecordService = serviceRecordService;
	}

	/** {@code GET /api/service-records?productId=} — newest service first. */
	@GetMapping
	public List<ServiceRecordResponse> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) UUID productId) {
		return serviceRecordService.list(CurrentUser.id(jwt), productId);
	}

	/** {@code POST /api/service-records} */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ServiceRecordResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ServiceRecordInput input) {
		return serviceRecordService.create(CurrentUser.id(jwt), input);
	}

	/** {@code PUT /api/service-records/{id}} — replace all of the record's details. */
	@PutMapping("/{id}")
	public ServiceRecordResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody ServiceRecordInput input) {
		return serviceRecordService.update(CurrentUser.id(jwt), id, input);
	}

	/** {@code DELETE /api/service-records/{id}} */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		serviceRecordService.delete(CurrentUser.id(jwt), id);
	}
}
