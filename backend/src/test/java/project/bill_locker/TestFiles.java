package project.bill_locker;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Builds real files for tests: PDFs with text, photos of text, scans, barcodes and QR codes. */
public final class TestFiles {

	private TestFiles() {
	}

	/** A PDF whose text is stored inside it, like an e-invoice. */
	public static byte[] pdfWithText(String... lines) {
		try (PDDocument pdf = new PDDocument()) {
			PDPage page = new PDPage(PDRectangle.A4);
			pdf.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.setLeading(18);
				content.newLineAtOffset(50, 780);
				for (String line : lines) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			return save(pdf);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A "scanned" PDF: one page that is only a picture of the text. */
	public static byte[] scannedPdfWithText(String... lines) {
		BufferedImage image = imageWithText(lines);
		try (PDDocument pdf = new PDDocument()) {
			// The picture fills a page as if it were scanned at 200 dpi.
			PDPage page = new PDPage(new PDRectangle(image.getWidth() * 72f / 200, image.getHeight() * 72f / 200));
			pdf.addPage(page);
			PDImageXObject picture = LosslessFactory.createFromImage(pdf, image);
			try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
				content.drawImage(picture, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
			}
			return save(pdf);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static byte[] passwordProtectedPdf() {
		try (PDDocument pdf = Loader.loadPDF(pdfWithText("Secret invoice"))) {
			StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
			policy.setEncryptionKeyLength(128);
			pdf.protect(policy);
			return save(pdf);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A photo of black text on white paper. */
	public static byte[] pngWithText(String... lines) {
		return png(imageWithText(lines));
	}

	/** A photo of text with a barcode or QR code under it, drawn with ZXing's writer. */
	public static byte[] pngWithCode(BarcodeFormat format, String content, String... lines) {
		BufferedImage text = imageWithText(lines);
		BufferedImage code = codeImage(format, content);
		BufferedImage image = new BufferedImage(Math.max(text.getWidth(), code.getWidth() + 120),
				text.getHeight() + code.getHeight() + 60, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.drawImage(text, 0, 0, null);
		graphics.drawImage(code, 60, text.getHeight() + 20, null);
		graphics.dispose();
		return png(image);
	}

	/** A PDF with text inside and a QR code picture on the page, like a GST e-invoice. */
	public static byte[] pdfWithQrCode(String content, String... lines) {
		try (PDDocument pdf = Loader.loadPDF(pdfWithText(lines))) {
			PDPage page = pdf.getPage(0);
			PDImageXObject qr = LosslessFactory.createFromImage(pdf, codeImage(BarcodeFormat.QR_CODE, content));
			try (PDPageContentStream drawing = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true)) {
				drawing.drawImage(qr, 330, 560, 220, 220);
			}
			return save(pdf);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * What a GST e-invoice QR code contains: a token shaped like a signed JWT whose middle
	 * part holds the invoice data. (Not really signed: tests only need the shape.)
	 */
	public static String eInvoiceQrContent(String invoiceNumber, String ddMMyyyy, String total) {
		String data = ("{\"SellerGstin\":\"29ABCDE1234F1Z5\",\"BuyerGstin\":\"URP\",\"DocNo\":\"%s\","
				+ "\"DocTyp\":\"INV\",\"DocDt\":\"%s\",\"TotInvVal\":%s,\"ItemCnt\":1,\"MainHsnCode\":\"8418\"}")
				.formatted(invoiceNumber, ddMMyyyy, total);
		String payload = "{\"data\":\"" + data.replace("\"", "\\\"") + "\",\"iss\":\"NIC\"}";
		Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
		return base64.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
				+ base64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "."
				+ base64.encodeToString("not-a-real-signature".getBytes(StandardCharsets.UTF_8));
	}

	private static BufferedImage codeImage(BarcodeFormat format, String content) {
		boolean square = format == BarcodeFormat.QR_CODE;
		try {
			BitMatrix matrix = new MultiFormatWriter().encode(content, format, square ? 520 : 700, square ? 520 : 160);
			BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
			for (int x = 0; x < matrix.getWidth(); x++) {
				for (int y = 0; y < matrix.getHeight(); y++) {
					image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
				}
			}
			return image;
		}
		catch (WriterException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	/** A photo with nothing written on it. */
	public static byte[] blankPng() {
		BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.dispose();
		return png(image);
	}

	private static BufferedImage imageWithText(String... lines) {
		int lineHeight = 70;
		BufferedImage image = new BufferedImage(1600, 120 + lines.length * lineHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setColor(Color.BLACK);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 44));
		for (int i = 0; i < lines.length; i++) {
			graphics.drawString(lines[i], 60, 90 + i * lineHeight);
		}
		graphics.dispose();
		return image;
	}

	private static byte[] png(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static byte[] save(PDDocument pdf) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		pdf.save(out);
		return out.toByteArray();
	}
}
