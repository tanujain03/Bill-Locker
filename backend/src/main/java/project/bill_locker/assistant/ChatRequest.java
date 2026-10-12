package project.bill_locker.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The chat so far (newest last); the browser sends at most the last 10 messages. */
public record ChatRequest(@NotEmpty @Size(max = 10) List<@Valid ChatMessage> messages) {
}
