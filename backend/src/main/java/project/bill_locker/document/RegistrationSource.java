package project.bill_locker.document;

/** Where a product's warranty registration link came from; the page shows it next to the link. */
public enum RegistrationSource {
	/** Printed on the bill (read by the AI). */
	DOCUMENT,
	/** In a QR code on the bill (decoded exactly). */
	QR_CODE,
	/** Not on the bill: found on the brand's website with a web search, and checked that it opens. */
	WEB_SEARCH,
	/** Nothing reliable found: a Google search for "<brand> warranty registration", so there's always a next step. */
	SEARCH,
	/** Typed or changed by the user. */
	USER
}
