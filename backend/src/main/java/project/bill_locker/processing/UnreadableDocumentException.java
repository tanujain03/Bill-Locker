package project.bill_locker.processing;

/** A file we can't read for a reason the user can fix; the message is shown to them as is. */
public class UnreadableDocumentException extends RuntimeException {

	public UnreadableDocumentException(String userMessage) {
		super(userMessage);
	}
}
