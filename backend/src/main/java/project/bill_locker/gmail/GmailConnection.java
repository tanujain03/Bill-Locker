package project.bill_locker.gmail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.user.User;

/**
 * A user's Gmail connection (at most one per user). Google tokens never reach the
 * browser: only the AES-GCM encrypted refresh token is stored here.
 */
@Entity
@Table(name = "gmail_connections",
		uniqueConstraints = @UniqueConstraint(name = "uk_gmail_connections_user", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class GmailConnection extends AuditableEntity {

	@NotNull
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_connections_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** The connected Gmail address. */
	@NotBlank
	@Email
	@Size(max = 254)
	@Column(name = "email", nullable = false, length = 254)
	private String email;

	/** Encrypted refresh token (nonce + ciphertext) — never the plain token. */
	@NotNull
	@Column(name = "refresh_token_ciphertext", nullable = false)
	private byte[] refreshTokenCiphertext;

	/** Which encryption key produced the ciphertext (supports key rotation). */
	@Column(name = "token_key_version", nullable = false)
	private int tokenKeyVersion = 1;

	@NotBlank
	@Size(max = 1000)
	@Column(name = "granted_scopes", nullable = false, length = 1000)
	private String grantedScopes;

	@Column(name = "auto_sync", nullable = false)
	private boolean autoSync = true;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "sync_status", nullable = false, length = 10)
	private GmailSyncStatus syncStatus = GmailSyncStatus.IDLE;

	@Column(name = "sync_started_at")
	private Instant syncStartedAt;

	@Column(name = "last_synced_at")
	private Instant lastSyncedAt;

	/** Gmail history id for incremental scans. */
	@Size(max = 40)
	@Column(name = "last_history_id", length = 40)
	private String lastHistoryId;

	@Size(max = 500)
	@Column(name = "last_error", length = 500)
	private String lastError;

	public GmailConnection(User user, String email, byte[] refreshTokenCiphertext, String grantedScopes) {
		this.user = user;
		this.email = email;
		this.refreshTokenCiphertext = refreshTokenCiphertext;
		this.grantedScopes = grantedScopes;
	}

	public void startSync() {
		syncStatus = GmailSyncStatus.SYNCING;
		syncStartedAt = Instant.now();
		lastError = null;
	}

	public void finishSync(String historyId) {
		syncStatus = GmailSyncStatus.IDLE;
		lastSyncedAt = Instant.now();
		lastHistoryId = historyId;
	}

	public void failSync(String error) {
		syncStatus = GmailSyncStatus.ERROR;
		lastError = error;
	}
}
