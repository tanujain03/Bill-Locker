package project.bill_locker.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import project.bill_locker.common.AuditableEntity;

/** A registered account holder. Every user-owned row points back to a user. */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditableEntity {

	public static final String DEFAULT_TIME_ZONE = "Asia/Kolkata";

	@NotBlank
	@Size(min = 2, max = 80)
	@Column(name = "name", nullable = false, length = 80)
	private String name;

	/** Always stored lower-case, so uniqueness is case-insensitive. */
	@NotBlank
	@Email
	@Size(max = 254)
	@Column(name = "email", nullable = false, length = 254)
	private String email;

	/** BCrypt hash — the plain password is never stored. */
	@NotBlank
	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	/** IANA zone used to decide "today" for warranty status and reminders. */
	@NotBlank
	@Column(name = "time_zone", nullable = false, length = 64)
	private String timeZone = DEFAULT_TIME_ZONE;

	public User(String name, String email, String passwordHash) {
		this.name = name;
		setEmail(email);
		this.passwordHash = passwordHash;
	}

	public void setEmail(String email) {
		this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * "Today" for this user — pass it to {@code Warranty.statusOn}. A UTC server would
	 * otherwise be a day behind for Indian users between 00:00 and 05:30 IST.
	 */
	public LocalDate today(Clock clock) {
		return LocalDate.now(clock.withZone(ZoneId.of(timeZone)));
	}
}
