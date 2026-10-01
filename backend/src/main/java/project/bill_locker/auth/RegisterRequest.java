package project.bill_locker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}. The annotations are checked before the
 * controller method runs ({@code @Valid}); broken rules come back as 400
 * VALIDATION_ERROR with a message per field.
 */
public record RegisterRequest(
		@NotBlank(message = "Name is required")
		@Size(min = 2, max = 80, message = "Name must be 2–80 characters")
		String name,

		@NotBlank(message = "Email is required")
		@Email(message = "Enter a valid email address")
		@Size(max = 254, message = "Email is too long")
		String email,

		@NotBlank(message = "Password is required")
		@Size(min = 8, max = 128, message = "Password must be 8–128 characters")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Password needs at least one letter and one number")
		String password) {
}
