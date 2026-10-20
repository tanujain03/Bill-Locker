package project.bill_locker.pricecompare;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record StoreLinkRequest(@NotNull @Valid PriceComparisonRequest search,
 @NotBlank @Size(max=3000) String url,@NotBlank @Size(max=120) String seller) {}
