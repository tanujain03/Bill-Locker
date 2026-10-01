package project.bill_locker.auth;

import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.security.TokenService;
import project.bill_locker.user.User;
import project.bill_locker.user.UserRepository;
import project.bill_locker.user.UserResponse;

/** Creates accounts and checks passwords. Both return a fresh login token. */
@Service
public class AuthService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	/** Checked when an email is unknown, so that answer takes as long as a wrong password. */
	private final String dummyPasswordHash;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = User.normalizeEmail(request.email());
		if (users.existsByEmail(email)) {
			throw emailAlreadyRegistered();
		}
		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
		try {
			users.saveAndFlush(user); // INSERT INTO users ... now, so a duplicate email fails right here
		}
		catch (DataIntegrityViolationException ex) {
			throw emailAlreadyRegistered(); // someone registered the same email a moment earlier
		}
		return loggedIn(user);
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
		if (user == null) {
			passwordEncoder.matches(request.password(), dummyPasswordHash);
			throw invalidCredentials();
		}
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw invalidCredentials();
		}
		return loggedIn(user);
	}

	private AuthResponse loggedIn(User user) {
		TokenService.IssuedToken token = tokenService.issueFor(user.getId());
		return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.from(user));
	}

	private static ApiException emailAlreadyRegistered() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account with this email already exists.",
				Map.of("email", "An account with this email already exists."));
	}

	/** The same answer for an unknown email and a wrong password: nobody can find out which emails have accounts. */
	private static ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password.");
	}
}
