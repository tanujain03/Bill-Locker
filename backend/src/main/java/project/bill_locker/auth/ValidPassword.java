package project.bill_locker.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The password rules, written once and used by both sign-up and reset password:
 * 8–128 characters with at least one letter and one number.
 */
@NotBlank
@Size(min = 8, max = 128)
@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$")
@ReportAsSingleViolation // one message instead of one per broken rule
@Constraint(validatedBy = {})
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

	String message() default "Use 8+ characters with at least one letter and one number";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
