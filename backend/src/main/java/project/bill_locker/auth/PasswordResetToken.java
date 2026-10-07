package project.bill_locker.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.BaseEntity;
import project.bill_locker.user.User;

/**
 * One "forgot password" request, stored in {@code password_reset_tokens}.
 *
 * <p>We keep only a SHA-256 <em>hash</em> of the token that is in the emailed link,
 * like a password: someone who reads this table still can't reset anyone's password.
 * A row exists only while its link is usable; using it (or asking for a new one)
 * deletes it.
 */
@Entity
@Table(name = "password_reset_tokens",
		uniqueConstraints = @UniqueConstraint(name = "uk_password_reset_tokens_token_hash", columnNames = "token_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_password_reset_tokens_user"))
	@OnDelete(action = OnDeleteAction.CASCADE) // deleting a user deletes their reset tokens
	private User user;

	/** 64 hex characters = SHA-256 of the token in the link. */
	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	public PasswordResetToken(User user, String tokenHash, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}
}
