package project.bill_locker.gmail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.user.User;

/**
 * One Gmail sign-in in progress. Google's redirect back to us carries no login token,
 * so this random "state" is how the backend knows which user is connecting. It can be
 * used once, within 10 minutes. (Its id is the state itself, so it has no UUID.)
 */
@Entity
@Table(name = "gmail_oauth_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GmailOAuthState {

	static final Duration VALID_FOR = Duration.ofMinutes(10);

	@Id
	@Column(name = "state", nullable = false, updatable = false, length = 64)
	private String state;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_oauth_states_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** The PKCE secret: only whoever started the sign-in can finish it. */
	@Column(name = "code_verifier", nullable = false, length = 64)
	private String codeVerifier;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	public GmailOAuthState(String state, User user, String codeVerifier) {
		this.state = state;
		this.user = user;
		this.codeVerifier = codeVerifier;
		this.expiresAt = Instant.now().plus(VALID_FOR);
	}

	public boolean isExpired() {
		return Instant.now().isAfter(expiresAt);
	}
}
