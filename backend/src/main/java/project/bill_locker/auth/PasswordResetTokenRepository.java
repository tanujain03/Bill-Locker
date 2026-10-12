package project.bill_locker.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import project.bill_locker.user.User;

/** Database access for {@link PasswordResetToken}; Spring Data writes the SQL from the method names. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/** Makes every earlier link of this user stop working. */
	void deleteByUser(User user);
}
