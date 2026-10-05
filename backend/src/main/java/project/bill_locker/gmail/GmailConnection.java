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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.user.User;

/**
 * A user's connected Gmail account (at most one). Google tokens never reach the browser,
 * and only the encrypted refresh token is stored ({@link TokenCipher}).
 */
@Entity
@Table(name = "gmail_connections",
		uniqueConstraints = @UniqueConstraint(name = "uk_gmail_connections_user", columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GmailConnection extends AuditableEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_connections_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** The connected Gmail address. */
	@Column(name = "email", nullable = false, length = 254)
	private String email;

	/** The refresh token, encrypted (nonce + ciphertext); never the plain token. */
	@Column(name = "refresh_token_ciphertext", nullable = false)
	private byte[] refreshTokenCiphertext;

	/** Scan every morning by itself. */
	@Column(name = "auto_sync", nullable = false)
	private boolean autoSync = true;

	@Enumerated(EnumType.STRING)
	@Column(name = "sync_status", nullable = false, length = 10)
	private GmailSyncStatus syncStatus = GmailSyncStatus.IDLE;

	@Column(name = "last_synced_at")
	private Instant lastSyncedAt;

	/** Why the last scan failed, in words the user can act on. */
	@Column(name = "last_error", length = 500)
	private String lastError;

	public GmailConnection(User user) {
		this.user = user;
	}

	/** (Re)connected: remember the account and token, and scan the inbox for bills. */
	public void connect(String email, byte[] refreshTokenCiphertext) {
		this.email = email;
		this.refreshTokenCiphertext = refreshTokenCiphertext;
		startSync();
	}

	/** The background scanner picks up connections that are SYNCING. */
	public void startSync() {
		syncStatus = GmailSyncStatus.SYNCING;
		lastError = null;
	}

	public void finishSync() {
		syncStatus = GmailSyncStatus.IDLE;
		lastSyncedAt = Instant.now();
	}

	public void failSync(String reason) {
		syncStatus = GmailSyncStatus.ERROR;
		lastError = reason;
	}

	public void setAutoSync(boolean autoSync) {
		this.autoSync = autoSync;
	}
}
