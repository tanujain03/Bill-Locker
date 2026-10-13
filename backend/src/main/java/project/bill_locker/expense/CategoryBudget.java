package project.bill_locker.expense;

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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.BaseEntity;
import project.bill_locker.document.BillCategory;
import project.bill_locker.user.User;

/**
 * A monthly spending limit for one category ("Food: ₹6,000 a month"), stored in
 * {@code category_budgets}. At most one per user and category.
 */
@Entity
@Table(name = "category_budgets", uniqueConstraints = @UniqueConstraint(name = "uk_category_budgets_user_category",
		columnNames = {"user_id", "category"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryBudget extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_category_budgets_user"))
	@OnDelete(action = OnDeleteAction.CASCADE) // deleting a user deletes their limits
	private User user;

	/** Plain text like documents.category, so a new category needs no database change. */
	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 30)
	private BillCategory category;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	CategoryBudget(User user, BillCategory category, BigDecimal amount) {
		this.user = user;
		this.category = category;
		this.amount = amount;
	}

	void setAmount(BigDecimal amount) {
		this.amount = amount;
	}
}
