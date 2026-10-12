package project.bill_locker.auth;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/reset-password}: the token from the link + the new password. */
public record ResetPasswordRequest(
		@NotBlank(message = "The reset link is missing its token") String token,
		@ValidPassword String password) {
}
