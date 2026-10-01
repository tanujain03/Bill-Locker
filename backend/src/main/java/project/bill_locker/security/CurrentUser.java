package project.bill_locker.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Who is calling. It always comes from the verified token, never from the request body. */
public final class CurrentUser {

	private CurrentUser() {
	}

	/** The signed-in user's id: the token's "sub" (subject) claim. */
	public static UUID id(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
