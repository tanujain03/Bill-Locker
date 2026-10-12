package project.bill_locker.assistant;

/** The assistant's SQL was refused: by our own check, or by the database (the message says why). */
public class QueryRejectedException extends RuntimeException {

	public QueryRejectedException(String message) {
		super(message);
	}
}
