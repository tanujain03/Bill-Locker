package project.bill_locker.gmail;

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
 * The one-time link between clicking "Connect" and Google's redirect back, stored in
 * {@code gmail_connect_states}. Like a password-reset token we keep only the hash of
 * the state; the PKCE verifier stays here because Google needs it at the code exchange.
 */
@Entity
@Table(name = "gmail_connect_states", uniqueConstraints = @UniqueConstraint(
		name = "uk_gmail_connect_states_state_hash", columnNames = "state_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GmailConnectState extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_connect_states_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** 64 hex characters = SHA-256 of the state sent to Google. */
	@Column(name = "state_hash", nullable = false, length = 64)
	private String stateHash;

	@Column(name = "code_verifier", nullable = false, length = 128)
	private String codeVerifier;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	/** SHA-256 of the nonce in the browser's cookie: the callback must come from the browser that clicked Connect. */
	@Column(name = "browser_hash", length = 64)
	private String browserHash;

	public GmailConnectState(User user, String stateHash, String codeVerifier, String browserHash,
			Instant expiresAt) {
		this.user = user;
		this.stateHash = stateHash;
		this.codeVerifier = codeVerifier;
		this.browserHash = browserHash;
		this.expiresAt = expiresAt;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	/** Only tests need to move the deadline. */
	void expireAt(Instant when) {
		this.expiresAt = when;
	}
}
