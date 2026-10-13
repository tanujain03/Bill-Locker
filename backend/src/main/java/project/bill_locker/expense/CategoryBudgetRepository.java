package project.bill_locker.expense;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import project.bill_locker.document.BillCategory;

public interface CategoryBudgetRepository extends JpaRepository<CategoryBudget, UUID> {

	List<CategoryBudget> findByUserId(UUID userId);

	Optional<CategoryBudget> findByUserIdAndCategory(UUID userId, BillCategory category);
}
