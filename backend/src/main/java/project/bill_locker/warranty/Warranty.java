package project.bill_locker.warranty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.document.Document;
import project.bill_locker.product.Product;

/**
 * A product's warranty: how many months, starting on the purchase date. The expiry
 * date is always calculated from those two ({@link WarrantyDates}); the status is
 * calculated whenever it is shown, because it changes every day.
 */
@Entity
@Table(name = "warranties",
		uniqueConstraints = @UniqueConstraint(name = "uk_warranties_product", columnNames = "product_id"),
		indexes = @Index(name = "idx_warranties_expiry", columnList = "expiry_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Warranty extends AuditableEntity {

	/**
	 * One warranty per product (the unique constraint above makes sure). Deleting the
	 * product deletes its warranty (ON DELETE CASCADE).
	 */
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_warranties_product"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Product product;

	/** Null when unknown ("Not found" on the bill, and not entered). */
	@Column(name = "warranty_months")
	private Integer warrantyMonths;

	@Column(name = "start_date")
	private LocalDate startDate;

	/** Stored as well as calculated, so the database can sort and filter by it. */
	@Column(name = "expiry_date")
	private LocalDate expiryDate;

	/** The bill this warranty was confirmed from. Deleting that bill keeps the warranty (SET NULL). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_document_id", foreignKey = @ForeignKey(name = "fk_warranties_source_document"))
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Document sourceDocument;

	public Warranty(Product product) {
		this.product = product;
	}

	/** The only way to change the dates, so the expiry date always matches months and start. */
	public void setPeriod(Integer months, LocalDate startDate) {
		this.warrantyMonths = months;
		this.startDate = startDate;
		this.expiryDate = WarrantyDates.expiryDate(startDate, months);
	}

	public void setSourceDocument(Document document) {
		this.sourceDocument = document;
	}

	public WarrantyStatus statusOn(LocalDate today) {
		return WarrantyDates.status(expiryDate, today);
	}

	public Long daysRemainingOn(LocalDate today) {
		return WarrantyDates.daysRemaining(expiryDate, today);
	}
}
