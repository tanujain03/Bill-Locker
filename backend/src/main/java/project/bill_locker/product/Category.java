package project.bill_locker.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import project.bill_locker.common.BaseEntity;

/** Global product category (reference data, shared by all users). */
@Entity
@Table(name = "categories", uniqueConstraints = {
		@UniqueConstraint(name = "uk_categories_name", columnNames = "name"),
		@UniqueConstraint(name = "uk_categories_slug", columnNames = "slug")
})
@Getter
@Setter
@NoArgsConstructor
public class Category extends BaseEntity {

	@NotBlank
	@Size(max = 60)
	@Column(name = "name", nullable = false, length = 60)
	private String name;

	/** Stable key the frontend maps to an icon, e.g. {@code home-appliances}. */
	@NotBlank
	@Size(max = 40)
	@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
	@Column(name = "slug", nullable = false, length = 40)
	private String slug;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	public Category(String name, String slug, int sortOrder) {
		this.name = name;
		this.slug = slug;
		this.sortOrder = sortOrder;
	}
}
