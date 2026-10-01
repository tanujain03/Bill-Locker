package project.bill_locker.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for users. Spring Data writes the SQL from the method names,
 * e.g. {@code findByEmail} becomes {@code select ... from users where email = ?}.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
