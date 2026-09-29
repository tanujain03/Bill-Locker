package project.bill_locker.service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.document.Document;
import project.bill_locker.product.Product;

/** A repair or maintenance visit; {@link #nextServiceDate} drives SERVICE_DUE reminders. */
@Entity
@Table(name = "service_records", indexes = {
		@Index(name = "idx_service_records_product_date", columnList = "product_id, service_date DESC"),
		@Index(name = "idx_service_records_next", columnList = "next_service_date")
})
@Getter
@Setter
@NoArgsConstructor
public class ServiceRecord extends AuditableEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_service_records_product"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Product product;

	/** Optional receipt for this visit (e.g. a SERVICE_RECEIPT document). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "document_id", foreignKey = @ForeignKey(name = "fk_service_records_document"))
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Document document;

	@NotNull
	@Column(name = "service_date", nullable = false)
	private LocalDate serviceDate;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "service_type", nullable = false, length = 24)
	private ServiceType serviceType;

	@Size(max = 120)
	@Column(name = "service_center", length = 120)
	private String serviceCenter;

	@PositiveOrZero
	@Digits(integer = 10, fraction = 2)
	@Column(name = "cost", precision = 12, scale = 2)
	private BigDecimal cost;

	@Column(name = "next_service_date")
	private LocalDate nextServiceDate;

	@Size(max = 500)
	@Column(name = "notes", length = 500)
	private String notes;

	public ServiceRecord(LocalDate serviceDate, ServiceType serviceType) {
		this.serviceDate = serviceDate;
		this.serviceType = serviceType;
	}

	@AssertTrue(message = "The next service must be after the service date")
	public boolean isNextServiceAfterServiceDate() {
		return nextServiceDate == null || serviceDate == null || nextServiceDate.isAfter(serviceDate);
	}
}
