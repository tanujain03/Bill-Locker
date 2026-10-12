package project.bill_locker.document.registration;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Finds the web links inside QR codes on a bill ("Scan to register your warranty").
 * ZXing reads the code exactly, which an AI looking at the picture can't do reliably.
 * A PDF's first pages are drawn as images first (PDFBox). Looking for codes never stops
 * a bill from being read: on any problem it simply finds none.
 */
@Component
public class QrCodeReader {

	private static final Logger log = LoggerFactory.getLogger(QrCodeReader.class);

	/** Registration QR codes sit on the first page or two. */
	private static final int MAX_PAGES = 3;
	private static final int PDF_DPI = 200;
	private static final Map<DecodeHintType, Object> HINTS = Map.of(
			DecodeHintType.TRY_HARDER, Boolean.TRUE,
			DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));

	/** The http(s) links in the file's QR codes, each once, in the order found. */
	public List<String> links(byte[] file, String contentType) {
		try {
			Set<String> links = new LinkedHashSet<>(); // the same code on two pages counts once
			for (BufferedImage image : images(file, contentType)) {
				for (Result result : decode(image)) {
					String text = result.getText().strip();
					String lower = text.toLowerCase(Locale.ROOT);
					// Only web links: other QR codes (UPI payments, signed GST e-invoices) aren't pages to open.
					if ((lower.startsWith("https://") || lower.startsWith("http://")) && text.length() <= 1000
							&& !text.contains(" ")) {
						links.add(text);
					}
				}
			}
			return List.copyOf(links);
		} catch (IOException | RuntimeException e) {
			log.warn("Could not look for QR codes: {}", e.toString());
			return List.of();
		}
	}

	/** A photo is one image; a PDF's first pages are drawn as images. */
	private static List<BufferedImage> images(byte[] file, String contentType) throws IOException {
		if ("application/pdf".equals(contentType)) {
			try (PDDocument pdf = Loader.loadPDF(file)) {
				PDFRenderer renderer = new PDFRenderer(pdf);
				List<BufferedImage> pages = new ArrayList<>();
				for (int page = 0; page < Math.min(pdf.getNumberOfPages(), MAX_PAGES); page++) {
					pages.add(renderer.renderImageWithDPI(page, PDF_DPI, ImageType.RGB));
				}
				return pages;
			}
		}
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(file)); // JPEG/PNG (WebP isn't supported: none found)
		return image == null ? List.of() : List.of(image);
	}

	private static List<Result> decode(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
		try {
			// "Multiple": a bill can carry several codes (payment, e-invoice, registration).
			return List.of(new GenericMultipleBarcodeReader(new MultiFormatReader()).decodeMultiple(bitmap, HINTS));
		} catch (NotFoundException none) {
			return List.of();
		}
	}
}
