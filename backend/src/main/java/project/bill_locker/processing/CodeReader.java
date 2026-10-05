package project.bill_locker.processing;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import project.bill_locker.document.ScannedCode;
import project.bill_locker.document.ScannedCode.EInvoice;
import project.bill_locker.document.ScannedCode.Kind;
import tools.jackson.databind.json.JsonMapper;

/**
 * Finds the barcodes and QR codes in an uploaded file with ZXing ("zebra crossing"), a
 * plain-Java library. Codes are read exactly, so they can correct what OCR misread.
 * Looking for codes never stops a document from being read: on any problem it finds none.
 */
@Component
public class CodeReader {

	private static final Logger log = LoggerFactory.getLogger(CodeReader.class);

	/** Codes are near the top of a bill (the e-invoice QR is on page 1). */
	private static final int MAX_PAGES = 3;
	private static final int PDF_DPI = 200;
	/** Bigger photos are scaled down first: still enough detail for codes, much less memory. */
	private static final int MAX_SIDE = 3000;
	/**
	 * Only the codes Bill Locker uses: QR / Data Matrix (links, e-invoices) and Code 128 / Code 39
	 * (serial-number stickers). Not shop codes (EAN/UPC): they are the same on every box, and
	 * looking for them in printed text gives false hits (a line of text "read" as EAN-8).
	 */
	private static final List<BarcodeFormat> FORMATS = List.of(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
			BarcodeFormat.CODE_128, BarcodeFormat.CODE_39);
	private static final Set<BarcodeFormat> SQUARE_CODES = Set.of(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX);
	/** header.payload.signature, each part in URL-safe Base64: the shape of a signed e-invoice QR. */
	private static final Pattern SIGNED_TOKEN = Pattern.compile("^[A-Za-z0-9_-]+\\.([A-Za-z0-9_-]+)\\.[A-Za-z0-9_-]+$");
	private static final DateTimeFormatter E_INVOICE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final JsonMapper json;

	public CodeReader(JsonMapper json) {
		this.json = json;
	}

	public List<ScannedCode> read(byte[] file, String mimeType) {
		try {
			Map<String, ScannedCode> found = new LinkedHashMap<>(); // the same code on two pages counts once
			for (BufferedImage image : images(file, mimeType)) {
				for (Result result : decode(image)) {
					ScannedCode code = describe(result.getBarcodeFormat(), result.getText());
					found.putIfAbsent(code.format() + ":" + code.value(), code);
				}
			}
			return List.copyOf(found.values());
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Could not look for codes: {}", ex.toString());
			return List.of();
		}
	}

	/** What the code is: a GST e-invoice, a web link, a barcode, or other text. */
	ScannedCode describe(BarcodeFormat format, String text) {
		String value = text.strip();
		if (!SQUARE_CODES.contains(format)) {
			return new ScannedCode(format.name(), Kind.BARCODE, value, null);
		}
		EInvoice invoice = eInvoice(value);
		if (invoice != null) {
			return new ScannedCode(format.name(), Kind.GST_E_INVOICE, value, invoice);
		}
		String lower = value.toLowerCase(Locale.ROOT);
		boolean link = lower.startsWith("https://") || lower.startsWith("http://");
		return new ScannedCode(format.name(), link ? Kind.LINK : Kind.TEXT, value, null);
	}

	/**
	 * A GST e-invoice QR code holds a signed token (JWT): three Base64 parts. The middle one
	 * is JSON like {"data": "{\"DocNo\":\"INV-1\",\"DocDt\":\"15/09/2026\",\"TotInvVal\":50738.82,…}"}.
	 * The signature isn't checked here; the values are still only suggestions to review.
	 */
	private EInvoice eInvoice(String value) {
		Matcher token = SIGNED_TOKEN.matcher(value);
		if (!token.matches()) {
			return null;
		}
		try {
			Map<?, ?> payload = json.readValue(Base64.getUrlDecoder().decode(token.group(1)), Map.class);
			Object data = payload.get("data");
			Map<?, ?> fields = data instanceof String text ? json.readValue(text, Map.class) : (Map<?, ?>) data;
			if (fields == null || fields.get("DocNo") == null) {
				return null;
			}
			return new EInvoice(String.valueOf(fields.get("DocNo")), isoDate(fields.get("DocDt")),
					amount(fields.get("TotInvVal")), fields.get("SellerGstin") == null ? null : String.valueOf(fields.get("SellerGstin")));
		}
		catch (RuntimeException notAnEInvoice) {
			return null;
		}
	}

	private static String isoDate(Object value) {
		try {
			return value == null ? null : LocalDate.parse(String.valueOf(value), E_INVOICE_DATE).toString();
		}
		catch (RuntimeException notADate) {
			return null;
		}
	}

	private static BigDecimal amount(Object value) {
		try {
			return value == null ? null : new BigDecimal(String.valueOf(value));
		}
		catch (NumberFormatException notANumber) {
			return null;
		}
	}

	/** A photo is one image; for a PDF, the first pages are drawn as images. */
	private static List<BufferedImage> images(byte[] file, String mimeType) throws IOException {
		if ("application/pdf".equals(mimeType)) {
			try (PDDocument pdf = Loader.loadPDF(file)) {
				PDFRenderer renderer = new PDFRenderer(pdf);
				List<BufferedImage> pages = new ArrayList<>();
				for (int page = 0; page < Math.min(pdf.getNumberOfPages(), MAX_PAGES); page++) {
					pages.add(renderer.renderImageWithDPI(page, PDF_DPI, ImageType.RGB));
				}
				return pages;
			}
		}
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(file));
		return image == null ? List.of() : List.of(image);
	}

	/** Every code in the image, of any of the {@link #FORMATS}. */
	private static List<Result> decode(BufferedImage original) {
		BufferedImage image = scaledDown(original);
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
		Map<DecodeHintType, Object> hints = Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE,
				DecodeHintType.POSSIBLE_FORMATS, FORMATS);
		try {
			return List.of(new GenericMultipleBarcodeReader(new MultiFormatReader()).decodeMultiple(bitmap, hints));
		}
		catch (NotFoundException none) {
			return List.of();
		}
	}

	private static BufferedImage scaledDown(BufferedImage image) {
		int longest = Math.max(image.getWidth(), image.getHeight());
		if (longest <= MAX_SIDE) {
			return image;
		}
		double scale = (double) MAX_SIDE / longest;
		int width = (int) Math.round(image.getWidth() * scale);
		int height = (int) Math.round(image.getHeight() * scale);
		BufferedImage smaller = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = smaller.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(image, 0, 0, width, height, null);
		graphics.dispose();
		return smaller;
	}
}
