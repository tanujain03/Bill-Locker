package project.bill_locker.processing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Gets the text out of an uploaded file.
 *
 * <ul>
 *   <li><b>PDF with text inside</b> (most e-invoices): PDFBox reads the text exactly. Fast, no OCR errors.</li>
 *   <li><b>Scanned PDF</b> (pages are pictures): each page is turned into an image and read with OCR.</li>
 *   <li><b>Photo</b> (JPG, PNG, WEBP): read with OCR.</li>
 * </ul>
 */
@Component
public class TextReader {

	/** Bills are short; reading stops after this many pages. */
	private static final int MAX_PAGES = 10;
	/** A page with less text than this is treated as a scan. */
	private static final int MIN_CHARACTERS_PER_PAGE = 25;
	/** Resolution for turning scanned pages into images: 300 dpi is what OCR likes best. */
	private static final int SCAN_DPI = 300;

	private final OcrEngine ocr;

	public TextReader(OcrEngine ocr) {
		this.ocr = ocr;
		// Makes ImageIO find the WEBP reader inside the app's packaged jar (it only looks once by default).
		ImageIO.scanForPlugins();
	}

	public ReadText read(byte[] file, String mimeType) {
		if ("application/pdf".equals(mimeType)) {
			return readPdf(file);
		}
		return new ReadText(clean(ocr.read(openImage(file))), "OCR");
	}

	private ReadText readPdf(byte[] file) {
		try (PDDocument pdf = Loader.loadPDF(file)) {
			int pages = Math.min(pdf.getNumberOfPages(), MAX_PAGES);
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true); // keep words in reading order, line by line
			stripper.setEndPage(pages);
			String text = clean(stripper.getText(pdf));
			if (text.replaceAll("\\s", "").length() >= MIN_CHARACTERS_PER_PAGE * pages) {
				return new ReadText(text, "PDF text");
			}

			// Hardly any text inside: the pages are pictures (a scan), so read each one with OCR.
			PDFRenderer renderer = new PDFRenderer(pdf);
			StringBuilder scanned = new StringBuilder();
			for (int page = 0; page < pages; page++) {
				BufferedImage image = renderer.renderImageWithDPI(page, SCAN_DPI, ImageType.GRAY);
				scanned.append(ocr.read(image)).append('\n');
			}
			return new ReadText(clean(scanned.toString()), "OCR of a scanned PDF");
		}
		catch (InvalidPasswordException ex) {
			throw new UnreadableDocumentException("This PDF is password-protected. Upload a copy without a password.");
		}
		catch (IOException ex) {
			throw new UnreadableDocumentException("This PDF could not be opened. It may be damaged.");
		}
	}

	private static BufferedImage openImage(byte[] file) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(file));
			if (image == null) {
				throw new UnreadableDocumentException("This image could not be opened.");
			}
			return image;
		}
		catch (IOException ex) {
			throw new UnreadableDocumentException("This image could not be opened. It may be damaged.");
		}
	}

	/** Tidies the text: one kind of line break, single spaces, no empty lines, no control characters. */
	static String clean(String text) {
		StringBuilder result = new StringBuilder();
		for (String line : text.replace(' ', ' ').split("\\R")) {
			String tidy = line.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
			if (!tidy.isEmpty()) {
				result.append(tidy).append('\n');
			}
		}
		return result.toString().strip();
	}
}
