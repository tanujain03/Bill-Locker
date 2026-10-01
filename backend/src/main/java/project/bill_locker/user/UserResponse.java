package project.bill_locker.user;

import java.time.Instant;
import java.util.UUID;

/**
 * A user as the API returns it (docs/api-contract.md, "User"). It deliberately
 * leaves out the password hash: entities never go straight to the browser.
 */
public record UserResponse(UUID id, String name, String email, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
	}
}
