package project.bill_locker.servicerecord;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceRecordRequest(
 @NotNull ServiceType type,
 @NotBlank @Size(max=200) String title,
 @Size(max=4000) String description,
 @NotNull LocalDate serviceDate,
 @Size(max=500) String providerName,
 @Size(max=500) String providerContact,
 @PositiveOrZero @Digits(integer=10, fraction=2) BigDecimal cost,
 Boolean coveredByWarranty,
 @Size(max=200) String claimNumber,
 ClaimStatus claimStatus,
 LocalDate nextDueDate,
 @Min(1) @Max(60) Integer repeatMonths,
 UUID attachmentDocumentId) {}
