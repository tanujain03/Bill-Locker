package project.bill_locker.product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.document.Document;
import project.bill_locker.service.ServiceRecord;
import project.bill_locker.user.User;
import project.bill_locker.warranty.Warranty;
import project.bill_locker.warranty.WarrantyType;

/**
 * Something the user owns. Only created/updated with confirmed data (manual entry
 * or a reviewed AI extraction) — AI suggestions stay on the {@link Document}.
 */
@Entity
@Table(name = "products", indexes = {
		@Index(name = "idx_products_user_created", columnList = "user_id, created_at DESC"),
		@Index(name = "idx_products_user_category", columnList = "user_id, category_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Product extends AuditableEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_products_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_products_category"))
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Category category;

	@NotBlank
	@Size(max = 120)
	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Size(max = 80)
	@Column(name = "brand", length = 80)
	private String brand;

	@Size(max = 80)
	@Column(name = "model", length = 80)
	private String model;

	@Size(max = 80)
	@Column(name = "serial_number", length = 80)
	private String serialNumber;

	@Column(name = "purchase_date")
	private LocalDate purchaseDate;

	@PositiveOrZero
	@Digits(integer = 10, fraction = 2)
	@Column(name = "purchase_price", precision = 12, scale = 2)
	private BigDecimal purchasePrice;

	@NotBlank
	@Pattern(regexp = "^[A-Z]{3}$")
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "INR";

	@Size(max = 120)
	@Column(name = "seller", length = 120)
	private String seller;

	@Size(max = 80)
	@Column(name = "invoice_number", length = 80)
	private String invoiceNumber;

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Warranty> warranties = new ArrayList<>();

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("serviceDate DESC")
	private List<ServiceRecord> serviceRecords = new ArrayList<>();

	/**
	 * Deliberately not cascaded: deleting a product only unlinks its bills
	 * (the database sets documents.product_id to NULL) — never lose a bill.
	 */
	@OneToMany(mappedBy = "product")
	private List<Document> documents = new ArrayList<>();

	public Product(User user, String name) {
		this.user = user;
		this.name = name;
	}

	public void addWarranty(Warranty warranty) {
		warranties.add(warranty);
		warranty.setProduct(this);
	}

	public void removeWarranty(Warranty warranty) {
		warranties.remove(warranty);
		warranty.setProduct(null);
	}

	/** Links a document to this product, keeping both sides of the relation in sync. */
	public void attachDocument(Document document) {
		if (!documents.contains(document)) {
			documents.add(document);
		}
		document.setProduct(this);
	}

	/** Never lose a bill: documents are unlinked, not deleted, when the product goes. */
	@PreRemove
	void unlinkDocuments() {
		documents.forEach(document -> document.setProduct(null));
		documents.clear();
	}

	public void addServiceRecord(ServiceRecord record) {
		serviceRecords.add(record);
		record.setProduct(this);
	}

	public void removeServiceRecord(ServiceRecord record) {
		serviceRecords.remove(record);
		record.setProduct(null);
	}

	/** The manufacturer warranty, if recorded. */
	public Optional<Warranty> standardWarranty() {
		return warranties.stream().filter(w -> w.getWarrantyType() == WarrantyType.STANDARD).findFirst();
	}

	/**
	 * The warranty shown for this product ({@code Product.warranty} in the API): the
	 * standard or extended cover that runs longest. Component-only cover (e.g.
	 * "10 years on compressor") never makes the whole product look covered.
	 */
	public Optional<Warranty> headlineWarranty() {
		return warranties.stream()
				.filter(w -> w.getWarrantyType() != WarrantyType.COMPONENT)
				.max(Comparator.comparing(Warranty::getExpiryDate, Comparator.nullsFirst(Comparator.naturalOrder()))
						.thenComparing(w -> w.getWarrantyType() == WarrantyType.STANDARD));
	}
}
