package project.bill_locker.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.bill_locker.common.AuditableEntity;

/**
 * A registered account, stored in the {@code users} table. Hibernate creates the
 * table from this class: each field below becomes a column.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs an empty constructor to load rows
public class User extends AuditableEntity {

	@Column(name = "name", nullable = false, length = 80)
	private String name;

	/** Always lower-case, so "Asha@Example.com" and "asha@example.com" are one account. */
	@Column(name = "email", nullable = false, length = 254)
	private String email;

	/** BCrypt hash of the password. The password itself is never stored. */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	public User(String name, String email, String passwordHash) {
		this.name = name;
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
	}

	public void rename(String newName) {
		this.name = newName;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
