package project.bill_locker.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.zxing.BarcodeFormat;
import org.junit.jupiter.api.Test;
import project.bill_locker.TestFiles;
import project.bill_locker.document.ScannedCode;
import project.bill_locker.document.ScannedCode.Kind;
import tools.jackson.databind.json.JsonMapper;

/** Real barcodes and QR codes, drawn by ZXing's writer and read back by CodeReader. */
class CodeReaderTests {

	private final CodeReader reader = new CodeReader(JsonMapper.builder().build());

	@Test
	void readsASerialNumberBarcodeInAPhoto() {
		byte[] sticker = TestFiles.pngWithCode(BarcodeFormat.CODE_128, "CHF2026-847291", "Serial No: CHF2026-847291");

		assertThat(reader.read(sticker, "image/png"))
				.extracting(ScannedCode::format, ScannedCode::kind, ScannedCode::value)
				.containsExactly(tuple("CODE_128", Kind.BARCODE, "CHF2026-847291"));
	}

	@Test
	void recognisesAWebLinkInAQrCode() {
		byte[] card = TestFiles.pngWithCode(BarcodeFormat.QR_CODE, "https://www.example.com/warranty/register?model=CH-320F",
				"Register your warranty");

		assertThat(reader.read(card, "image/png"))
				.extracting(ScannedCode::kind, ScannedCode::value)
				.containsExactly(tuple(Kind.LINK, "https://www.example.com/warranty/register?model=CH-320F"));
	}

	@Test
	void readsTheInvoiceDetailsInAGstEInvoiceQrCode() {
		byte[] invoice = TestFiles.pdfWithQrCode(
				TestFiles.eInvoiceQrContent("INV-2026-0915-1042", "15/09/2026", "50738.82"), "TAX INVOICE");

		ScannedCode code = reader.read(invoice, "application/pdf").getFirst();

		assertThat(code.kind()).isEqualTo(Kind.GST_E_INVOICE);
		assertThat(code.invoice().invoiceNumber()).isEqualTo("INV-2026-0915-1042");
		assertThat(code.invoice().invoiceDate()).isEqualTo("2026-09-15");
		assertThat(code.invoice().total()).isEqualByComparingTo("50738.82");
		assertThat(code.invoice().sellerGstin()).isEqualTo("29ABCDE1234F1Z5");
	}

	@Test
	void findsNothingInAPlainPhotoOrABrokenFile() {
		assertThat(reader.read(TestFiles.blankPng(), "image/png")).isEmpty();
		assertThat(reader.read(new byte[] {1, 2, 3}, "application/pdf")).as("no error, just no codes").isEmpty();
	}
}
