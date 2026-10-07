package project.bill_locker.document.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import project.bill_locker.document.DocumentDetails;
import project.bill_locker.document.DocumentType;

/** Turning Gemini's JSON answer into details, including the messy answers. */
class GeminiAnswerParserTests {

	@Test
	void parsesFullAnswer() {
		DocumentDetails d = GeminiAnswerParser.parse("""
				{"documentType": "INVOICE", "documentNumber": "INV-1029",
				 "sellerName": "Croma", "sellerAddress": "Mumbai", "sellerContact": "1800-123",
				 "buyerName": "Asha", "buyerAddress": "Pune", "buyerEmail": "asha@example.com",
				 "purchaseDate": "2026-01-10", "taxAmount": 228.66, "totalAmount": 1499,
				 "items": [
				   {"productName": "Phone", "modelNumber": "M-1", "serialNumber": "SN-1", "unitPrice": 1199,
				    "warrantyPeriodMonths": 12, "warrantyStartDate": "2026-01-10", "warrantyEndDate": "2027-01-09",
				    "warrantyProvider": "Samsung"},
				   {"productName": "Charger", "unitPrice": null}
				 ]}
				""");

		assertThat(d.documentType()).isEqualTo(DocumentType.INVOICE);
		assertThat(d.documentNumber()).isEqualTo("INV-1029");
		assertThat(d.buyerEmail()).isEqualTo("asha@example.com");
		assertThat(d.purchaseDate()).isEqualTo(LocalDate.of(2026, 1, 10));
		assertThat(d.taxAmount()).isEqualByComparingTo("228.66");
		assertThat(d.totalAmount()).isEqualByComparingTo("1499");
		assertThat(d.items()).hasSize(2);
		assertThat(d.items().get(0).serialNumber()).isEqualTo("SN-1");
		assertThat(d.items().get(0).warrantyPeriodMonths()).isEqualTo(12);
		assertThat(d.items().get(0).warrantyEndDate()).isEqualTo(LocalDate.of(2027, 1, 9));
		assertThat(d.items().get(1).productName()).isEqualTo("Charger");
		assertThat(d.items().get(1).unitPrice()).isNull();
	}

	@Test
	void cleansMoneyAndBadDates() {
		DocumentDetails d = GeminiAnswerParser.parse("""
				{"documentType": "bill", "totalAmount": "₹1,499.00", "taxAmount": "about 200",
				 "purchaseDate": "12th March", "sellerName": "   ",
				 "items": [{"productName": "Fan", "warrantyPeriodMonths": "24 months", "unitPrice": "Rs. 999"}]}
				""");

		assertThat(d.documentType()).isEqualTo(DocumentType.OTHER);
		assertThat(d.totalAmount()).isEqualByComparingTo(new BigDecimal("1499.00"));
		assertThat(d.taxAmount()).isEqualByComparingTo("200");
		assertThat(d.purchaseDate()).isNull();
		assertThat(d.sellerName()).isNull();
		assertThat(d.items().get(0).warrantyPeriodMonths()).isEqualTo(24);
		assertThat(d.items().get(0).unitPrice()).isEqualByComparingTo("999");
	}

	@Test
	void unreadableNumbersBecomeNull() {
		DocumentDetails d = GeminiAnswerParser.parse("""
				{"totalAmount": "n/a", "taxAmount": "1.2.3", "items": [{"warrantyPeriodMonths": "lifetime"}]}
				""");

		assertThat(d.totalAmount()).isNull();
		assertThat(d.taxAmount()).isNull();
		assertThat(d.items().get(0).warrantyPeriodMonths()).isNull();
	}

	@Test
	void valuesTooBigForTheirColumnsAreCutOrDropped() {
		// Columns hold 500 characters and amounts up to 10 digits before the point.
		String longAddress = "A".repeat(600);
		DocumentDetails d = GeminiAnswerParser.parse("""
				{"sellerAddress": "%s", "totalAmount": 1234567890123, "taxAmount": "₹12,34,56,78,90,123",
				 "items": [{"unitPrice": 99999999999.5, "warrantyPeriodMonths": 99999999999}]}
				""".formatted(longAddress));

		assertThat(d.sellerAddress()).hasSize(500);
		assertThat(d.totalAmount()).isNull();
		assertThat(d.taxAmount()).isNull();
		assertThat(d.items().get(0).unitPrice()).isNull();
		assertThat(d.items().get(0).warrantyPeriodMonths()).isNull();
	}

	@Test
	void missingItemsGivesEmptyList() {
		DocumentDetails d = GeminiAnswerParser.parse("{\"sellerName\": \"Croma\"}");

		assertThat(d.sellerName()).isEqualTo("Croma");
		assertThat(d.items()).isEmpty();
	}

	@Test
	void invalidJsonThrowsExtractionFailed() {
		assertThatThrownBy(() -> GeminiAnswerParser.parse("Sorry, I can't read this."))
				.isInstanceOf(ExtractionException.class)
				.extracting("code").isEqualTo(ExtractionException.FAILED);
	}
}
