package project.bill_locker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import project.bill_locker.document.DocumentDetails;
import project.bill_locker.document.DocumentItemView;
import project.bill_locker.document.DocumentType;
import project.bill_locker.document.ai.DetailExtractor;
import project.bill_locker.document.ai.ExtractionException;

/**
 * Stands in for Gemini in tests: no network, no API key, no free-tier quota used.
 * By default it "reads" a fixed invoice; a test can make it return something else
 * or fail.
 */
public class FakeDetailExtractor implements DetailExtractor {

	public static DocumentDetails sampleInvoice() {
		return new DocumentDetails(DocumentType.INVOICE, "INV-1029", "Croma", "Mumbai", "1800-123", "Asha",
				"Pune", "asha@example.com", LocalDate.of(2026, 1, 10), new BigDecimal("228.66"), new BigDecimal("1499.00"),
				List.of(
						// No end date on the bill: the backend works it out from start + 12 months.
						new DocumentItemView("Phone", "M-1", "SN-1", new BigDecimal("1199.00"), 12,
								LocalDate.of(2026, 1, 10), null, "Samsung"),
						new DocumentItemView("Charger", null, null, new BigDecimal("71.34"), null, null, null, null)));
	}

	private DocumentDetails answer;
	private ExtractionException failure;

	public FakeDetailExtractor() {
		reset();
	}

	@Override
	public DocumentDetails extract(byte[] file, String contentType) {
		if (failure != null) {
			throw failure;
		}
		return answer;
	}

	public void willReturn(DocumentDetails details) {
		this.answer = details;
		this.failure = null;
	}

	public void willFail(ExtractionException failure) {
		this.failure = failure;
	}

	public void reset() {
		willReturn(sampleInvoice());
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary // wins over the real Gemini extractor
		FakeDetailExtractor fakeDetailExtractor() {
			return new FakeDetailExtractor();
		}
	}
}
