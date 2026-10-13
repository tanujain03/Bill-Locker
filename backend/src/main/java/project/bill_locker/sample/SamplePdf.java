package project.bill_locker.sample;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import project.bill_locker.document.DocumentDetails;
import project.bill_locker.document.DocumentItemView;

/**
 * Draws a one-page PDF for a sample bill (PDFBox, already used for QR codes), so the document
 * page can preview and download it like a real bill. Every page says "SAMPLE BILL".
 * The built-in PDF fonts have no rupee sign, so amounts are written as "Rs.".
 */
final class SamplePdf {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private SamplePdf() {
	}

	static byte[] render(DocumentDetails bill) {
		var regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage(PDRectangle.A4);
			pdf.addPage(page);
			try (PDPageContentStream text = new PDPageContentStream(pdf, page)) {
				text.beginText();
				text.setLeading(18);
				text.newLineAtOffset(56, 780);
				text.setFont(bold, 11);
				text.showText("SAMPLE BILL - demo data of Bill Locker, not a real purchase");
				text.newLine();
				text.newLine();
				text.setFont(bold, 20);
				text.showText(bill.sellerName());
				text.newLine();
				text.setFont(regular, 11);
				text.showText(bill.documentType() == null ? "Bill" : switch (bill.documentType()) {
					case INVOICE -> "Tax invoice";
					case RECEIPT -> "Bill / receipt";
					case WARRANTY_CARD -> "Warranty card";
					case OTHER -> "Document";
				});
				text.showText("   No. " + bill.documentNumber() + "   Date: " + DATE.format(bill.purchaseDate()));
				text.newLine();
				text.newLine();
				for (DocumentItemView item : bill.items()) {
					text.setFont(bold, 12);
					text.showText(item.productName() + "   Rs. " + plain(item.unitPrice()));
					text.newLine();
					text.setFont(regular, 10);
					StringBuilder line = new StringBuilder();
					if (item.modelNumber() != null) {
						line.append("Model ").append(item.modelNumber()).append("   ");
					}
					if (item.serialNumber() != null) {
						line.append("S/N ").append(item.serialNumber()).append("   ");
					}
					if (item.warrantyPeriodMonths() != null) {
						line.append("Warranty ").append(item.warrantyPeriodMonths()).append(" months");
					}
					text.showText(line.toString());
					text.newLine();
				}
				text.newLine();
				if (bill.taxAmount() != null) {
					text.setFont(regular, 11);
					text.showText("GST included: Rs. " + plain(bill.taxAmount()));
					text.newLine();
				}
				text.setFont(bold, 14);
				text.showText("Total: Rs. " + plain(bill.totalAmount()));
				text.endText();
			}
			pdf.save(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String plain(BigDecimal amount) {
		return amount == null ? "-" : amount.toPlainString();
	}
}
