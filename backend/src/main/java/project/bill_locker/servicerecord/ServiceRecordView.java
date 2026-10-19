package project.bill_locker.servicerecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceRecordView(UUID id, UUID documentId, UUID productId, int itemPosition, String productName,
 ServiceType type, String title, String description, LocalDate serviceDate, String providerName,
 String providerContact, BigDecimal cost, boolean coveredByWarranty, String claimNumber, ClaimStatus claimStatus,
 LocalDate nextDueDate, Integer repeatMonths, UUID attachmentDocumentId, ServiceDueStatus dueStatus,
 boolean reminderActive) {}
