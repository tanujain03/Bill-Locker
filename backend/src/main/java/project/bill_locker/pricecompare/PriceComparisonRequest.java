package project.bill_locker.pricecompare;
import jakarta.validation.constraints.*;
public record PriceComparisonRequest(@NotBlank @Size(min=3,max=160) String query,@Size(max=80) String model) {}
