package project.bill_locker.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Email kind classification by subject, snippet, and file names. */
class EmailClassifierTests {

	private final EmailClassifier classifier = new EmailClassifier();

	@ParameterizedTest
	@CsvSource({
			// subject, fileNames, expected
			"Your Croma tax invoice, INV-22.pdf, INVOICE",
			"Order #123, Invoice_123.pdf, INVOICE",
			"Warranty card for your AC, card.jpg, WARRANTY",
			"Extended guarantee certificate, cert.pdf, WARRANTY",
			"Service report – washing machine, report.pdf, SERVICE",
			"Payment received, r.pdf, RECEIPT",
			"Your order has shipped, slip.pdf, ORDER",
			"Documents attached, scan.pdf, UNSURE",
			"Festive sale – 50% off, catalogue.pdf, DROP",
			"Your account statement, invoice_jan.pdf, INVOICE",
			"Billion-dollar ideas newsletter, x.pdf, DROP",
			"Documents attached, bank_statement.pdf, DROP",
			"Documents attached, Payslip_Jan.pdf, DROP",
			"Festive sale – invoice offers inside, catalogue.pdf, DROP",
			"Your account statement – invoice enclosed, scan.pdf, DROP",
	})
	void classifiesBySubjectFileNames(String subject, String fileName, String expectedStr) {
		EmailKind expected = "DROP".equals(expectedStr) ? null : EmailKind.valueOf(expectedStr);
		Optional<EmailKind> result = classifier.classify(subject, "", List.of(fileName));

		if (expected == null) {
			assertThat(result).isEmpty();
		} else {
			assertThat(result).hasValue(expected);
		}
	}
}
