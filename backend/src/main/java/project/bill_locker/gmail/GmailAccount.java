package project.bill_locker.gmail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.BaseEntity;
import project.bill_locker.user.User;

/**
 * One connected Gmail address, stored in {@code gmail_accounts}. A user may connect
 * several. Only the refresh token is kept, and only encrypted.
 */
@Entity
@Table(name = "gmail_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_gmail_accounts_user_email",
		columnNames = {"user_id", "email"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GmailAccount extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_gmail_accounts_user"))
	@OnDelete(action = OnDeleteAction.CASCADE) // deleting a user deletes their Gmail accounts
	private User user;

	@Column(name = "email", nullable = false, length = 254)
	private String email;

	/** Nonce + AES-GCM ciphertext (see TokenCipher). The plain token is never stored. */
	@Column(name = "refresh_token_ciphertext", nullable = false)
	private byte[] refreshTokenCiphertext;

	@Enumerated(EnumType.STRING)
	@Column(name = "scan_status", nullable = false, length = 20)
	private GmailScanStatus scanStatus;

	/** The start of the date range the queued scan covers. */
	@Column(name = "scan_since")
	private LocalDate scanSince;

	@Column(name = "last_scanned_at")
	private Instant lastScannedAt;

	@Column(name = "last_error", length = 500)
	private String lastError;

	public GmailAccount(User user, String email, byte[] refreshTokenCiphertext) {
		this.user = user;
		this.email = email;
		this.refreshTokenCiphertext = refreshTokenCiphertext;
		this.scanStatus = GmailScanStatus.IDLE;
	}

	/** Asks the background worker to scan emails received since that date. */
	public void queueScan(LocalDate since) {
		this.scanStatus = GmailScanStatus.QUEUED;
		this.scanSince = since;
		this.lastError = null;
	}

	public void startScan() {
		this.scanStatus = GmailScanStatus.SCANNING;
	}

	public void finishScan() {
		this.scanStatus = GmailScanStatus.IDLE;
		this.lastScannedAt = Instant.now();
	}

	/** The reason is words the user can act on, e.g. "Access was withdrawn. Connect again." */
	public void failScan(String reason) {
		this.scanStatus = GmailScanStatus.ERROR;
		this.lastError = reason;
	}

	/** Connecting the same address again replaces the token. */
	public void updateToken(byte[] cipher) {
		this.refreshTokenCiphertext = cipher;
	}
}
