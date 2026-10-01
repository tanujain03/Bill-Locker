package project.bill_locker.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.bill_locker.common.BaseEntity;

/** A product category such as "Kitchen Appliances". Shared by all users; see {@link DefaultCategories}. */
@Entity
@Table(name = "categories", uniqueConstraints = {
		@UniqueConstraint(name = "uk_categories_name", columnNames = "name"),
		@UniqueConstraint(name = "uk_categories_slug", columnNames = "slug")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

	@Column(name = "name", nullable = false, length = 60)
	private String name;

	/** A fixed key the frontend turns into an icon, e.g. {@code home-appliances}. */
	@Column(name = "slug", nullable = false, length = 40)
	private String slug;

	/** Categories are listed in this order. */
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	Category(String name, String slug, int sortOrder) {
		this.name = name;
		this.slug = slug;
		this.sortOrder = sortOrder;
	}
}
