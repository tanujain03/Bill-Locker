package project.bill_locker.gmail;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Guesses the kind of an email (invoice, warranty, etc.) from its subject, snippet,
 * and file names using keyword rules. Text is lowercased before matching.
 */
@Component
public class EmailClassifier {

	// Compiled patterns for efficiency, using word boundaries and whole-word matching
	private static final Pattern JUNK_PATTERN = Pattern.compile("\\b(" +
			"statement|newsletter|unsubscribe|otp|one time password|offer|sale|discount|payslip|salary slip" +
			")\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern INVOICE_PATTERN = Pattern.compile("\\b(" +
			"invoice|tax invoice|bill of supply" +
			")\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern WARRANTY_PATTERN = Pattern.compile("\\b(" +
			"warranty|guarantee" +
			")\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern SERVICE_PATTERN = Pattern.compile("\\b(" +
			"service|repair|job card" +
			")\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern RECEIPT_PATTERN = Pattern.compile("\\b(" +
			"receipt|cash memo|payment received" +
			")\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern ORDER_PATTERN = Pattern.compile("\\b(" +
			"order confirmation|your order|has shipped|delivered" +
			")\\b", Pattern.CASE_INSENSITIVE);

	/**
	 * Classifies an email into an EmailKind.
	 *
	 * @param subject     email subject; null treated as ""
	 * @param snippet     email snippet/preview; null treated as ""
	 * @param fileNames   attachment file names; file names with _, ., - are replaced with spaces
	 * @return kind of email, or empty if it should be dropped (junk without invoice file)
	 */
	public Optional<EmailKind> classify(String subject, String snippet, List<String> fileNames) {
		// Treat null as empty string
		String subj = subject == null ? "" : subject;
		String snip = snippet == null ? "" : snippet;

		// Combine text and convert to lowercase with Locale.ROOT
		String text = (subj + " " + snip).toLowerCase(Locale.ROOT);

		// Process file names: replace _, ., - with spaces
		String fileText = processFileNames(fileNames).toLowerCase(Locale.ROOT);

		// Combine all text
		String combined = text + " " + fileText;

		// Check junk first: if junk in combined text but no invoice file name, drop
		if (JUNK_PATTERN.matcher(combined).find() && !INVOICE_PATTERN.matcher(fileText).find()) {
			return Optional.empty();
		}

		// Check in order: WARRANTY, SERVICE, INVOICE, RECEIPT, ORDER, else UNSURE
		if (WARRANTY_PATTERN.matcher(combined).find()) {
			return Optional.of(EmailKind.WARRANTY);
		}
		if (SERVICE_PATTERN.matcher(combined).find()) {
			return Optional.of(EmailKind.SERVICE);
		}
		if (INVOICE_PATTERN.matcher(combined).find()) {
			return Optional.of(EmailKind.INVOICE);
		}
		if (RECEIPT_PATTERN.matcher(combined).find()) {
			return Optional.of(EmailKind.RECEIPT);
		}
		if (ORDER_PATTERN.matcher(combined).find()) {
			return Optional.of(EmailKind.ORDER);
		}

		return Optional.of(EmailKind.UNSURE);
	}

	/**
	 * Processes file names by replacing _, ., - with spaces to enable word-boundary matching.
	 * Ignores null file names.
	 */
	private String processFileNames(List<String> fileNames) {
		if (fileNames == null || fileNames.isEmpty()) {
			return "";
		}
		return fileNames.stream()
				.filter(name -> name != null)
				.map(name -> name.replaceAll("[_\\.-]", " "))
				.reduce((a, b) -> a + " " + b)
				.orElse("");
	}
}
