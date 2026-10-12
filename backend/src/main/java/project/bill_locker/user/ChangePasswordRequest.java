package project.bill_locker.user;

import jakarta.validation.constraints.NotBlank;
import project.bill_locker.auth.ValidPassword;

/**
 * Body of {@code PUT /api/users/me/password}. The current password proves it's really
 * the owner (not someone at an unlocked computer); the new one follows the sign-up rules.
 */
public record ChangePasswordRequest(
		@NotBlank(message = "Enter your current password")
		String currentPassword,

		@ValidPassword
		String newPassword) {
}
