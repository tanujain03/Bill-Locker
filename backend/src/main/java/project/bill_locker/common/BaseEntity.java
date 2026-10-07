package project.bill_locker.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Columns every table has: a UUID id (not guessable, unlike 1, 2, 3…) and when the
 * row was created and last changed. {@code @MappedSuperclass} means "not a table of
 * its own; copy these columns into each entity that extends me".
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

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Hibernate calls this just before the INSERT. */
	@PrePersist
	protected void onInsert() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	/** …and this just before an UPDATE. */
	@PreUpdate
	protected void onUpdate() {
		updatedAt = Instant.now();
	}
}
