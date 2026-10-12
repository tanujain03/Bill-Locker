package project.bill_locker.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/users/me}: the same name rules as sign-up. */
public record UpdateProfileRequest(
		@NotBlank(message = "Name is required")
		@Size(min = 2, max = 80, message = "Name must be 2–80 characters")
		String name) {
}
