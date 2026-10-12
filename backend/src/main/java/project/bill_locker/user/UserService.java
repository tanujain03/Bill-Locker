package project.bill_locker.user;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;

/** The signed-in user's own account: change the name, change the password. */
@Service
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		User user = load(userId);
		user.rename(request.name().trim()); // saved at the end of the transaction (JPA dirty checking)
		return UserResponse.from(user);
	}

	@Transactional
	public void changePassword(UUID userId, ChangePasswordRequest request) {
		User user = load(userId);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			// 400, not 401: a 401 means "your session is over" and the browser would sign you out.
			throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Your current password is not correct.",
					Map.of("currentPassword", "Your current password is not correct."));
		}
		user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
	}

	private User load(UUID userId) {
		return users.findById(userId)
				// The token is valid but the account is gone: treat it like being signed out.
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in to continue."));
	}
}
