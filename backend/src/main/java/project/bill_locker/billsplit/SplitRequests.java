package project.bill_locker.billsplit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public final class SplitRequests {
 private SplitRequests() {}
 public record Group(@NotBlank @Size(max=100) String name) {}
 public record Member(@NotBlank @Size(max=80) String name) {}
 public record Token(@NotBlank @Size(max=128) String token) {}
 public record Share(@NotNull UUID memberId,@PositiveOrZero @Digits(integer=10,fraction=2) BigDecimal amount) {}
 public record Expense(@NotNull UUID requestId,@NotBlank @Size(max=200) String title,
  @NotNull @Positive @Digits(integer=10,fraction=2) BigDecimal amount,@NotNull LocalDate expenseDate,
  @NotNull UUID payerId,@NotNull SplitMode mode,@NotEmpty @Size(max=50) List<@NotNull @Valid Share> shares,UUID sourceDocumentId) {}
 public record Settlement(@NotNull UUID requestId,@NotNull UUID fromMemberId,@NotNull UUID toMemberId,
  @NotNull @Positive @Digits(integer=10,fraction=2) BigDecimal amount) {}
}
