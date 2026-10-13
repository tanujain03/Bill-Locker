package project.bill_locker.expense;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.document.BillCategory;
import project.bill_locker.security.CurrentUser;

/** The Expenses page: the spending of one period, the monthly budget and the category limits. */
@RestController
@RequestMapping("/api/expenses")
class ExpenseController {

	private final ExpenseService expenses;

	ExpenseController(ExpenseService expenses) {
		this.expenses = expenses;
	}

	/** {@code from}/{@code to} are "YYYY-MM-DD", both included; without them, the current month. */
	@GetMapping
	ExpenseReport report(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		return expenses.report(CurrentUser.id(jwt), from, to);
	}

	@PutMapping("/budget")
	BudgetRequest setBudget(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BudgetRequest request) {
		expenses.setBudget(CurrentUser.id(jwt), request.amount());
		return request;
	}

	/** One category's monthly limit, e.g. PUT /api/expenses/budgets/FOOD {"amount": 6000}; null removes it. */
	@PutMapping("/budgets/{category}")
	BudgetRequest setCategoryBudget(@AuthenticationPrincipal Jwt jwt, @PathVariable BillCategory category,
			@Valid @RequestBody BudgetRequest request) {
		expenses.setCategoryBudget(CurrentUser.id(jwt), category, request.amount());
		return request;
	}
}
