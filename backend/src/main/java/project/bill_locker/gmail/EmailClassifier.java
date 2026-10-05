package project.bill_locker.gmail;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import project.bill_locker.document.DocumentType;

/**
 * Decides with simple rules whether an email probably holds a bill, from its subject,
 * preview and attachment names. The answer is a document type and how likely it is
 * (0..1). The app offers emails at 0.6 or more in "Import all"; nothing is imported
 * without the user choosing it.
 */
@Component
public class EmailClassifier {

	public record Verdict(DocumentType type, double confidence) {
	}

	/** Mail that mentions bills but isn't one: statements, offers, one-time passwords. */
	private static final Pattern NOT_A_BILL = word("statement|newsletter|unsubscribe|otp|one time password|"
			+ "offer|sale|discount code|payslip|salary slip");
	private static final Pattern WARRANTY = word("warranty card|warranty certificate|warranty registration|"
			+ "extended warranty|warranty");
	private static final Pattern REPAIR = word("repair|job card|job sheet");
	private static final Pattern SERVICE = word("service report|service receipt|service invoice|servicing");
	private static final Pattern INVOICE = word("tax invoice|invoice|bill of supply");
	private static final Pattern RECEIPT = word("receipt|cash memo|payment received|your bill");
	private static final Pattern ORDER = word("order confirmation|order confirmed|your order|order placed|"
			+ "has been delivered|has shipped");

	public Verdict classify(String subject, String snippet, List<GmailAttachment> attachments) {
		// "Invoice_123.pdf" becomes "Invoice 123 pdf": in a regex "_" counts as a letter, so a whole-word
		// match on "invoice" would miss it.
		String names = String.join(" ", attachments.stream().map(GmailAttachment::fileName).toList())
				.replaceAll("[_.\\-]", " ");
		String text = (subject + " " + snippet + " " + names).toLowerCase(Locale.ROOT);
		boolean invoiceFile = INVOICE.matcher(names.toLowerCase(Locale.ROOT)).find();

		if (NOT_A_BILL.matcher(text).find() && !invoiceFile) {
			return new Verdict(DocumentType.OTHER, 0.15);
		}
		if (WARRANTY.matcher(text).find()) {
			return new Verdict(DocumentType.WARRANTY_CARD, 0.85);
		}
		if (REPAIR.matcher(text).find()) {
			return new Verdict(DocumentType.REPAIR_RECEIPT, 0.8);
		}
		if (SERVICE.matcher(text).find()) {
			return new Verdict(DocumentType.SERVICE_RECEIPT, 0.8);
		}
		if (INVOICE.matcher(text).find()) {
			// An attached file called "...invoice..." is the strongest sign there is.
			return new Verdict(DocumentType.INVOICE, invoiceFile ? 0.95 : 0.85);
		}
		if (RECEIPT.matcher(text).find()) {
			return new Verdict(DocumentType.INVOICE, 0.75);
		}
		if (ORDER.matcher(text).find()) {
			return new Verdict(DocumentType.INVOICE, 0.65);
		}
		return new Verdict(DocumentType.OTHER, 0.3);
	}

	/** Whole words only: "bill" must not match "billion". */
	private static Pattern word(String alternatives) {
		return Pattern.compile("\\b(" + alternatives + ")\\b");
	}
}
