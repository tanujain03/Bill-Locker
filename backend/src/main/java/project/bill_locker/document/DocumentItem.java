package project.bill_locker.document;

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
import project.bill_locker.common.BaseEntity;

/**
 * One product on a bill, stored in {@code document_items}. It has its own table
 * because one invoice can list several products, each with its own serial number
 * and warranty.
 */
@Entity
@Table(name = "document_items", indexes = @Index(name = "idx_document_items_document", columnList = "document_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_document_items_document"))
	@OnDelete(action = OnDeleteAction.CASCADE) // deleting a document deletes its items
	private Document document;

	/** 0, 1, 2… keeps the products in the order the user (or the bill) lists them. */
	@Column(name = "position", nullable = false)
	private int position;

	@Column(name = "product_name", length = 500)
	private String productName;

	@Column(name = "model_number", length = 500)
	private String modelNumber;

	@Column(name = "serial_number", length = 500)
	private String serialNumber;

	@Column(name = "unit_price", precision = 12, scale = 2)
	private BigDecimal unitPrice;

	@Column(name = "warranty_period_months")
	private Integer warrantyPeriodMonths;

	@Column(name = "warranty_start_date")
	private LocalDate warrantyStartDate;

	@Column(name = "warranty_end_date")
	private LocalDate warrantyEndDate;

	@Column(name = "warranty_provider", length = 500)
	private String warrantyProvider;

	/** The manufacturer's brand ("Noise" for "Noise Buds VS104"): used to find its registration page. */
	@Column(name = "brand", length = 100)
	private String brand;

	/** The brand's warranty registration page (always http/https). */
	@Column(name = "registration_url", length = 1000)
	private String registrationUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "registration_source", length = 20)
	private RegistrationSource registrationSource;

	DocumentItem(Document document, int position, DocumentItemView values) {
		this.document = document;
		this.position = position;
		this.productName = values.productName();
		this.modelNumber = values.modelNumber();
		this.serialNumber = values.serialNumber();
		this.unitPrice = values.unitPrice();
		this.warrantyPeriodMonths = values.warrantyPeriodMonths();
		this.warrantyStartDate = values.warrantyStartDate();
		this.warrantyEndDate = values.warrantyEndDate();
		this.warrantyProvider = values.warrantyProvider();
		this.brand = values.brand();
		this.registrationUrl = values.registrationUrl();
		this.registrationSource = values.registrationUrl() == null ? null : values.registrationSource();
	}

	void setRegistration(String url, RegistrationSource source) {
		this.registrationUrl = url;
		this.registrationSource = source;
	}
}
