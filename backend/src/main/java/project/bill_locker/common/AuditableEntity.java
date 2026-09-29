package project.bill_locker.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;

/** Adds {@code updated_at} for entities that change after creation. */
@Getter
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	// Runs after BaseEntity#initCreatedAt (superclass callbacks run first).
	@PrePersist
	protected void initUpdatedAt() {
		updatedAt = getCreatedAt() != null ? getCreatedAt() : Instant.now();
	}

	@PreUpdate
	protected void touchUpdatedAt() {
		updatedAt = Instant.now();
	}
}
