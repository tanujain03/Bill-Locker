package project.bill_locker.auth;

import java.time.Instant;
import project.bill_locker.user.UserResponse;


public record AuthResponse(String token, String tokenType, Instant expiresAt, UserResponse user) {
}
