package project.bill_locker.product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.user.User;
import project.bill_locker.warranty.Warranty;

/**
 * Something the user owns, with its purchase details. A product only ever holds values
 * the user typed in or confirmed: what was read from a bill stays a suggestion on the
 * document until the user clicks "Confirm & Save".
 */
@Entity
@Table(name = "products", indexes = @Index(name = "idx_products_user_created", columnList = "user_id, created_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends AuditableEntity {

	/** Deleting the user deletes their products too (ON DELETE CASCADE). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_products_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** Optional. If a category were ever removed, its products would just lose it (SET NULL). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_products_category"))
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Category category;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "brand", length = 80)
	private String brand;

	@Column(name = "model", length = 80)
	private String model;

	@Column(name = "serial_number", length = 80)
	private String serialNumber;

	@Column(name = "purchase_date")
	private LocalDate purchaseDate;

	/** Money is a BigDecimal (exact), never a double, which can't store 0.1 exactly. */
	@Column(name = "purchase_price", precision = 12, scale = 2)
	private BigDecimal purchasePrice;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "INR";

	@Column(name = "seller", length = 120)
	private String seller;

	@Column(name = "invoice_number", length = 80)
	private String invoiceNumber;

	/**
	 * Every product has exactly one warranty row, in its own table (its months may be
	 * unknown). {@code cascade = ALL}: saving or deleting the product does the same to it.
	 */
	@OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	private Warranty warranty;

	public Product(User user, ProductInput input, Category category) {
		this.user = user;
		this.warranty = new Warranty(this);
		update(input, category);
	}

	/** Replaces all the details with what the user entered. Empty text is stored as null ("Not recorded"). */
	public void update(ProductInput input, Category category) {
		this.category = category;
		this.name = input.name().trim();
		this.brand = clean(input.brand());
		this.model = clean(input.model());
		this.serialNumber = clean(input.serialNumber());
		this.purchaseDate = input.purchaseDate();
		this.purchasePrice = input.purchasePrice();
		this.currency = input.currency() != null ? input.currency() : "INR";
		this.seller = clean(input.seller());
		this.invoiceNumber = clean(input.invoiceNumber());
		// The warranty starts on the purchase date.
		warranty.setPeriod(input.warrantyMonths(), input.purchaseDate());
	}

	private static String clean(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}
}
