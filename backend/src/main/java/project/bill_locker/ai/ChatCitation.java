package project.bill_locker.ai;

import java.util.UUID;

/**
 * A product or document an assistant answer was based on (ChatReference in the API).
 * Stored as JSON inside {@link ChatMessage#getCitations()}.
 */
public record ChatCitation(Type type, UUID id, String title, String subtitle) {

	public enum Type {
		PRODUCT,
		DOCUMENT
	}
}
