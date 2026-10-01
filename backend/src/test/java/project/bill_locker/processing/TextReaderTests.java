package project.bill_locker.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import project.bill_locker.TestFiles;

/** Getting text out of files, with the real PDFBox and the real Tesseract OCR (no Spring needed). */
class TextReaderTests {

	private static final TextReader reader = new TextReader(new OcrEngine());

	@Test
	void readsTheTextStoredInsideAPdf() {
		byte[] pdf = TestFiles.pdfWithText("TAX INVOICE", "Invoice No: ME/2026-27/006745", "Grand Total Rs. 8,999.00");

		ReadText read = reader.read(pdf, "application/pdf");

		assertThat(read.method()).isEqualTo("PDF text");
		assertThat(read.text()).contains("Invoice No: ME/2026-27/006745").contains("Grand Total Rs. 8,999.00");
	}

	@Test
	void readsAPhotoWithOcr() {
		byte[] photo = TestFiles.pngWithText("TAX INVOICE", "Invoice No: INV-2026-0042", "Grand Total 8,999.00");

		ReadText read = reader.read(photo, "image/png");

		assertThat(read.method()).isEqualTo("OCR");
		assertThat(read.text()).containsIgnoringCase("tax invoice").contains("INV-2026-0042").contains("8,999.00");
	}

	@Test
	void readsAScannedPdfWithOcr() {
		byte[] scan = TestFiles.scannedPdfWithText("WARRANTY CARD", "Serial No: PH9252X77821");

		ReadText read = reader.read(scan, "application/pdf");

		assertThat(read.method()).isEqualTo("OCR of a scanned PDF");
		assertThat(read.text()).containsIgnoringCase("warranty card").contains("PH9252X77821");
	}

	@Test
	void explainsWhyAFileCannotBeRead() {
		assertThatThrownBy(() -> reader.read(TestFiles.passwordProtectedPdf(), "application/pdf"))
				.isInstanceOf(UnreadableDocumentException.class)
				.hasMessageContaining("password-protected");
		assertThatThrownBy(() -> reader.read("not a pdf".getBytes(StandardCharsets.US_ASCII), "application/pdf"))
				.isInstanceOf(UnreadableDocumentException.class)
				.hasMessageContaining("could not be opened");
		assertThatThrownBy(() -> reader.read(new byte[] {1, 2, 3}, "image/png"))
				.isInstanceOf(UnreadableDocumentException.class)
				.hasMessageContaining("could not be opened");
	}

	@Test
	void tidiesTheTextLineByLine() {
		assertThat(TextReader.clean("  Grand \t Total  \r\n\r\n\u0007 8,999.00 ")).isEqualTo("Grand Total\n8,999.00");
	}
}
