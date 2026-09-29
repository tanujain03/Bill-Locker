package project.bill_locker.gmail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.user.User;

/**
 * Short-lived OAuth "state" for the Gmail consent flow. Google's callback carries
 * no JWT, so the state is how the backend knows which user is connecting.
 * Delete it once used; expired rows can be purged.
 */
@Entity
@Table(name = "gmail_oauth_states", indexes = @Index(name = "idx_gmail_oauth_states_expires", columnList = "expires_at"))
@Getter
@NoArgsConstructor
public class GmailOAuthState {

	@Id
	@Size(max = 128)
	@Column(name = "state", nullable = false, updatable = false, length = 128)
	private String state;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_oauth_states_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** PKCE verifier used when exchanging the authorization code. */
	@Size(max = 128)
	@Column(name = "code_verifier", length = 128)
	private String codeVerifier;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public GmailOAuthState(String state, User user, String codeVerifier, Duration validFor) {
		this.state = state;
		this.user = user;
		this.codeVerifier = codeVerifier;
		this.createdAt = Instant.now();
		this.expiresAt = createdAt.plus(validFor);
	}

	public boolean isExpired(Instant now) {
		return now.isAfter(expiresAt);
	}
}
