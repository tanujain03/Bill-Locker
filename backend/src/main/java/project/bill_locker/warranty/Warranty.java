package project.bill_locker.warranty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.document.Document;
import project.bill_locker.product.Product;

/**
 * A warranty period for a product. {@link #expiryDate} is always derived from
 * {@link #startDate} and {@link #warrantyMonths}; the status is computed on
 * demand with {@link #statusOn(LocalDate)} and never stored.
 */
@Entity
@Table(name = "warranties", indexes = {
		@Index(name = "idx_warranties_product", columnList = "product_id"),
		@Index(name = "idx_warranties_expiry", columnList = "expiry_date")
})
@Getter
@Setter
@NoArgsConstructor
public class Warranty extends AuditableEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_warranties_product"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Product product;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "warranty_type", nullable = false, length = 20)
	private WarrantyType warrantyType = WarrantyType.STANDARD;

	/** What the cover applies to, e.g. "Digital inverter compressor". */
	@Size(max = 200)
	@Column(name = "coverage_note", length = 200)
	private String coverageNote;

	@Min(0)
	@Max(240)
	@Column(name = "warranty_months")
	private Integer warrantyMonths;

	/** Usually the purchase (or delivery) date. */
	@Column(name = "start_date")
	private LocalDate startDate;

	@Setter(AccessLevel.NONE)
	@Column(name = "expiry_date")
	private LocalDate expiryDate;

	/** The document the warranty was read from (invoice / warranty card). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_document_id", foreignKey = @ForeignKey(name = "fk_warranties_source_document"))
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Document sourceDocument;

	public Warranty(WarrantyType warrantyType, Integer warrantyMonths, LocalDate startDate) {
		this.warrantyType = warrantyType;
		this.warrantyMonths = warrantyMonths;
		this.startDate = startDate;
		deriveExpiryDate();
	}

	public void setWarrantyMonths(Integer warrantyMonths) {
		this.warrantyMonths = warrantyMonths;
		deriveExpiryDate();
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
		deriveExpiryDate();
	}

	@PrePersist
	@PreUpdate
	void deriveExpiryDate() {
		expiryDate = WarrantyDates.expiryDate(startDate, warrantyMonths);
	}

	public WarrantyStatus statusOn(LocalDate today) {
		return WarrantyDates.status(expiryDate, today);
	}

	public Long daysRemainingOn(LocalDate today) {
		return WarrantyDates.daysRemaining(expiryDate, today);
	}
}
