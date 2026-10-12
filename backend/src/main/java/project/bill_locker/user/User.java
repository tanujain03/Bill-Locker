package project.bill_locker.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.bill_locker.common.BaseEntity;


@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	@Column(name = "name", nullable = false, length = 80)
	private String name;

	@Column(name = "email", nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	/** How much the user plans to spend per month (Expenses page); null = no budget set. */
	@Column(name = "monthly_budget", precision = 12, scale = 2)
	private BigDecimal monthlyBudget;

	public User(String name, String email, String passwordHash) {
		this.name = name;
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
	}

	public void rename(String newName) {
		this.name = newName;
	}

	public void changePasswordHash(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
	}

	public void setMonthlyBudget(BigDecimal amount) {
		this.monthlyBudget = amount;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
