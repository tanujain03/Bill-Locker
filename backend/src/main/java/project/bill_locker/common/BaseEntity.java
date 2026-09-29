package project.bill_locker.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.proxy.HibernateProxy;

/**
 * Primary key and creation time shared by all entities.
 * UUID ids are not guessable and serialise as the string ids the API contract uses.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	protected void initCreatedAt() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	/** Id-based equality that also works when one side is a Hibernate proxy. */
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || effectiveClass(this) != effectiveClass(other)) {
			return false;
		}
		UUID thisId = getId();
		return thisId != null && thisId.equals(((BaseEntity) other).getId());
	}

	@Override
	public int hashCode() {
		return effectiveClass(this).hashCode();
	}

	private static Class<?> effectiveClass(Object object) {
		return object instanceof HibernateProxy proxy
				? proxy.getHibernateLazyInitializer().getPersistentClass()
				: object.getClass();
	}
}
