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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.product.Product;

/**
 * A repair or maintenance visit for a product. The {@code nextServiceDate} of a
 * product's most recent record is when its next service is due (and the reminder).
 */
@Entity
@Table(name = "service_records",
		indexes = @Index(name = "idx_service_records_product_date", columnList = "product_id, service_date DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceRecord extends AuditableEntity {

	/** Deleting the product deletes its service history too (ON DELETE CASCADE). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_service_records_product"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Product product;

	@Column(name = "service_date", nullable = false)
	private LocalDate serviceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "service_type", nullable = false, length = 24)
	private ServiceType serviceType;

	@Column(name = "service_center", length = 120)
	private String serviceCenter;

	@Column(name = "cost", precision = 12, scale = 2)
	private BigDecimal cost;

	@Column(name = "next_service_date")
	private LocalDate nextServiceDate;

	@Column(name = "notes", length = 500)
	private String notes;

	public ServiceRecord(Product product, ServiceRecordInput input) {
		update(product, input);
	}

	/** Replaces all the details. Empty text is stored as null. */
	public void update(Product product, ServiceRecordInput input) {
		this.product = product;
		this.serviceDate = input.serviceDate();
		this.serviceType = input.serviceType();
		this.serviceCenter = clean(input.serviceCenter());
		this.cost = input.cost();
		this.nextServiceDate = input.nextServiceDate();
		this.notes = clean(input.notes());
	}

	private static String clean(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}
}
