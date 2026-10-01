package project.bill_locker.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import project.bill_locker.document.DocumentType;
import project.bill_locker.document.ExtractionResult;

/** The rules that find details in text, tried on typical bills. */
class DetailExtractorTests {

	private final DetailExtractor extractor = new DetailExtractor();

	@Test
	void readsATypicalShopTaxInvoice() {
		ExtractionResult result = extractor.extract("""
				METRO ELECTRONICS
				12, MG Road, Bengaluru 560001
				GSTIN: 29ABCDE1234F1Z5
				TAX INVOICE
				Invoice No: ME/2026-27/006745
				Invoice Date: 27/09/2026
				Sold By: Metro Electronics, Bengaluru
				Description Qty Rate Amount
				Philips Air Fryer HD9252/90 1 8,999.00 8,999.00
				Model No: HD9252/90
				Serial No: PH9252X77821
				Sub Total 7,626.27
				CGST 9% 686.36
				SGST 9% 686.36
				Grand Total ₹8,999.00
				2 Years Warranty from the date of purchase
				""");

		assertThat(result.documentType()).isEqualTo(DocumentType.INVOICE);
		assertThat(result.invoiceNumber()).isEqualTo("ME/2026-27/006745");
		assertThat(result.purchaseDate()).isEqualTo("2026-09-27");
		assertThat(result.purchasePrice()).isEqualByComparingTo("8999.00");
		assertThat(result.currency()).isEqualTo("INR");
		assertThat(result.seller()).isEqualTo("Metro Electronics, Bengaluru");
		assertThat(result.brand()).isEqualTo("Philips");
		assertThat(result.productName()).isEqualTo("Philips Air Fryer HD9252/90");
		assertThat(result.model()).isEqualTo("HD9252/90");
		assertThat(result.serialNumber()).isEqualTo("PH9252X77821");
		assertThat(result.warrantyMonths()).isEqualTo(24);
		// Labelled values are trusted more than the product-name guess.
		assertThat(result.confidence()).containsEntry("invoiceNumber", 0.85).containsEntry("productName", 0.5);
	}

	@Test
	void readsAnOnlineOrderInvoice() {
		ExtractionResult result = extractor.extract("""
				Tax Invoice/Bill of Supply/Cash Memo
				(Original for Recipient)
				Sold By :
				Appario Retail Private Ltd
				Order Number: 403-1234567-1234567
				Order Date: 15.09.2026
				Invoice Number : DEL5-123456
				Invoice Date : 15.09.2026
				Samsung 253 L 3 Star Frost Free Double Door Refrigerator (RT28C3053S8) ₹24,990.00 1 ₹24,990.00
				TOTAL: ₹24,990.00
				Amount in Words: Twenty-four Thousand Nine Hundred Ninety only
				""");

		assertThat(result.invoiceNumber()).as("the invoice number, not the order number").isEqualTo("DEL5-123456");
		assertThat(result.purchaseDate()).isEqualTo("2026-09-15");
		assertThat(result.purchasePrice()).isEqualByComparingTo("24990.00");
		assertThat(result.seller()).as("name on the line after the label").isEqualTo("Appario Retail Private Ltd");
		assertThat(result.brand()).isEqualTo("Samsung");
		assertThat(result.productName())
				.isEqualTo("Samsung 253 L 3 Star Frost Free Double Door Refrigerator (RT28C3053S8)");
		assertThat(result.warrantyMonths()).isNull();
		assertThat(result.confidence()).doesNotContainKey("warrantyMonths");
	}

	@Test
	void readsAWarrantyCard() {
		ExtractionResult result = extractor.extract("""
				WARRANTY CARD
				Product: LG 1.5 Ton 5 Star Inverter Split AC
				Model: PS-Q19YNZE
				Serial No. 305KAXY4M512
				Date of Purchase: 3 Aug 2026
				Warranty: 1 Year comprehensive, 10 years on compressor
				""");

		assertThat(result.documentType()).isEqualTo(DocumentType.WARRANTY_CARD);
		assertThat(result.brand()).isEqualTo("LG");
		assertThat(result.productName()).isEqualTo("LG 1.5 Ton 5 Star Inverter Split AC");
		assertThat(result.model()).isEqualTo("PS-Q19YNZE");
		assertThat(result.serialNumber()).isEqualTo("305KAXY4M512");
		assertThat(result.purchaseDate()).isEqualTo("2026-08-03");
		assertThat(result.warrantyMonths()).as("the whole-product cover, not the compressor").isEqualTo(12);
		assertThat(result.purchasePrice()).isNull();
	}

	@Test
	void readsTheLabelledDetailsOfAPhotographedInvoice() {
		// Real OCR output of an invoice image: "₹" came out as "%" and there are stray marks,
		// but the "Product Details" box lists everything as "Label : value".
		ExtractionResult result = extractor.extract("""
				F Sample Invoice
				HomeTech Electronics
				Your Home. Our Technology. RETAIL PURCHASE INVOICE
				@ Shop No. 12, Green Park Market, InvoiceNo. —-:_ INV-2026-0915-1042
				MG Road, Jaipur - 302001
				Invoice Date : 15 September 2026
				\\ +91 98765 43210
				i Pi tt Mode : UPI
				®&%_support@hometech.in ia
				Customer Details @ Warranty Information
				Name a Warranty Period : 2 Years
				Address 2 = Coverage : Manufacturer Warranty
				(Parts & Service)
				# Product Brand Model SerialNumber Quantity aa cae
				1 Refrigerator © CoolHome © CH-320F © CHF2026-847291 1 42,999.00 42,999.00
				Sub Total % 42,999.00
				Taxes (GST 18%) = 7,739.82
				Total Amount % 50,738.82
				Product Details
				Product : Refrigerator
				Brand : CoolHome
				Model : CH-320F
				Serial number : CHF2026-847291
				Purchase date : 15 September 2026
				Price : %42,999
				Seller : HomeTech Electronics
				Invoice number : INV-2026-0915-1042
				Warranty : 2 Years
				Thank you for choosing HomeTech Electronics! eee
				""");

		assertThat(result.documentType()).isEqualTo(DocumentType.INVOICE);
		assertThat(result.productName()).isEqualTo("Refrigerator");
		assertThat(result.brand()).as("not a well-known brand, but labelled").isEqualTo("CoolHome");
		assertThat(result.model()).isEqualTo("CH-320F");
		assertThat(result.serialNumber()).isEqualTo("CHF2026-847291");
		assertThat(result.purchaseDate()).isEqualTo("2026-09-15");
		assertThat(result.purchasePrice()).as("the product's price, not the total with tax").isEqualByComparingTo("42999");
		assertThat(result.currency()).as("a GST bill is in rupees").isEqualTo("INR");
		assertThat(result.seller()).isEqualTo("HomeTech Electronics");
		assertThat(result.invoiceNumber()).isEqualTo("INV-2026-0915-1042");
		assertThat(result.warrantyMonths()).isEqualTo(24);
		assertThat(result.confidence()).containsEntry("productName", 0.85).containsEntry("brand", 0.85);
	}

	@Test
	void skipsStrayOcrMarksBetweenALabelAndItsValue() {
		assertThat(extractor.extract("InvoiceNo. —-:_ INV-2026-0915-1042").invoiceNumber()).isEqualTo("INV-2026-0915-1042");
		assertThat(extractor.extract("Model No. :- CH-320F").model()).isEqualTo("CH-320F");
	}

	@Test
	void guessesTheProductLineForABrandFoundByItsLabel() {
		ExtractionResult result = extractor.extract("""
				Brand: CoolHome
				CoolHome Double Door Refrigerator 1 42,999.00
				""");

		assertThat(result.brand()).isEqualTo("CoolHome");
		assertThat(result.productName()).isEqualTo("CoolHome Double Door Refrigerator");
	}

	@Test
	void findsNothingInTextWithoutDetails() {
		ExtractionResult result = extractor.extract("Thank you for shopping with us!");

		assertThat(result.documentType()).isEqualTo(DocumentType.OTHER);
		assertThat(result.invoiceNumber()).isNull();
		assertThat(result.purchaseDate()).isNull();
		assertThat(result.purchasePrice()).isNull();
		assertThat(result.confidence()).containsOnlyKeys("documentType");
	}

	@Test
	void understandsTheCommonDateFormats() {
		assertThat(DetailExtractor.datesIn("27/09/2026")).containsExactly(LocalDate.of(2026, 9, 27));
		assertThat(DetailExtractor.datesIn("09/27/2026")).as("month first").containsExactly(LocalDate.of(2026, 9, 27));
		assertThat(DetailExtractor.datesIn("2026-09-27")).containsExactly(LocalDate.of(2026, 9, 27));
		assertThat(DetailExtractor.datesIn("27-Sept-2026")).containsExactly(LocalDate.of(2026, 9, 27));
		assertThat(DetailExtractor.datesIn("Sep 27, 2026")).containsExactly(LocalDate.of(2026, 9, 27));
		assertThat(DetailExtractor.datesIn("31/02/2026")).as("not a real date").isEmpty();
		assertThat(DetailExtractor.datesIn("01/01/2099")).as("in the future").isEmpty();
	}
}
