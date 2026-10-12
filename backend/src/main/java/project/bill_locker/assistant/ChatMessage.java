package project.bill_locker.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One chat bubble: from the user, or an earlier answer from the assistant. */
public record ChatMessage(
		@NotBlank @Pattern(regexp = "user|assistant", message = "must be user or assistant") String role,
		@NotBlank @Size(max = 2000) String text) {
}
