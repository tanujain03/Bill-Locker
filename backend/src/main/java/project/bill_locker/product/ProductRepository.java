package project.bill_locker.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Database access for products. Every query includes the owner's id. */
public interface ProductRepository extends JpaRepository<Product, UUID> {

	/** The product only if it belongs to this user. The entity graph loads warranty and category with it. */
	@EntityGraph(attributePaths = {"warranty", "category"})
	Optional<Product> findByIdAndUserId(UUID id, UUID userId);

	/**
	 * A user's products, newest first. "join fetch" loads each product's warranty and
	 * category in the same query (not one extra query per product). A null filter means
	 * "any"; {@code search} is a lower-case pattern such as {@code %dell%}.
	 */
	@Query("""
			select p from Product p
			left join fetch p.warranty
			left join fetch p.category
			where p.user.id = :userId
			  and (:categoryId is null or p.category.id = :categoryId)
			  and (:search is null
			       or lower(p.name) like :search or lower(p.brand) like :search or lower(p.model) like :search
			       or lower(p.seller) like :search or lower(p.serialNumber) like :search
			       or lower(p.invoiceNumber) like :search)
			order by p.createdAt desc
			""")
	List<Product> findForUser(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId,
			@Param("search") String search);
}
