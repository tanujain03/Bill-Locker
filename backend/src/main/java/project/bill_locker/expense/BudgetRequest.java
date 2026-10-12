package project.bill_locker.expense;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** PUT /api/expenses/budget: the monthly budget in rupees; null removes it. */
public record BudgetRequest(
		@Positive(message = "Enter an amount above zero") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
}
