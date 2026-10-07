package project.bill_locker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/forgot-password}. */
public record ForgotPasswordRequest(
		@NotBlank(message = "Email is required")
		@Email(message = "Enter a valid email address")
		String email) {
}
