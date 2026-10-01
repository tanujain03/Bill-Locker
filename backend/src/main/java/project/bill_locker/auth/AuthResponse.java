package project.bill_locker.auth;

import java.time.Instant;
import project.bill_locker.user.UserResponse;

/**
 * Returned by register and login (docs/api-contract.md, "AuthResponse"). The
 * frontend stores {@code token} and sends it as {@code Authorization: Bearer <token>}.
 */
public record AuthResponse(String token, String tokenType, Instant expiresAt, UserResponse user) {
}
