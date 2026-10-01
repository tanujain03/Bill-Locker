package project.bill_locker.user;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;

/** Reads and updates the signed-in user's profile. */
@Service
public class UserService {

	private final UserRepository users;

	public UserService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public UserResponse getProfile(UUID userId) {
		return UserResponse.from(findUser(userId));
	}

	@Transactional
	public UserResponse rename(UUID userId, String newName) {
		User user = findUser(userId);
		user.rename(newName.trim());
		// No save() call needed: inside a transaction, Hibernate writes changed fields when it commits.
		return UserResponse.from(user);
	}

	/** The account behind a valid token. If it was deleted meanwhile, the user must sign in again. */
	public User findUser(UUID userId) {
		return users.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again."));
	}
}
