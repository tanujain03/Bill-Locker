package project.bill_locker.processing;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;
import org.springframework.stereotype.Component;

/**
 * Reads the text in a picture with Tesseract OCR, through the Tess4J library.
 * Tess4J ships Tesseract and its English language data inside its jar, so nothing
 * extra has to be installed on Windows.
 */
@Component
public class OcrEngine {

	/** Bigger photos are scaled down first: just as readable, much faster. */
	private static final int MAX_SIDE_PX = 3000;

	private final Tesseract tesseract = new Tesseract();

	public OcrEngine() {
		// Unpacks the language data (tessdata/eng.traineddata) from the jar into a temp folder.
		File languageData = LoadLibs.extractTessResources("tessdata");
		tesseract.setDatapath(languageData.getAbsolutePath());
		tesseract.setLanguage("eng");
		// Phone photos rarely record their resolution; telling Tesseract avoids a guess (and a warning).
		tesseract.setVariable("user_defined_dpi", "300");
	}

	/** The text in the image. One call at a time: a Tesseract instance is not thread-safe. */
	public synchronized String read(BufferedImage image) {
		try {
			return tesseract.doOCR(prepare(image));
		}
		catch (TesseractException ex) {
			throw new IllegalStateException("Tesseract could not read the image", ex);
		}
	}

	/** Gray pixels on a white background (transparent images too), at most MAX_SIDE_PX wide or high. */
	private static BufferedImage prepare(BufferedImage image) {
		double scale = Math.min(1.0, (double) MAX_SIDE_PX / Math.max(image.getWidth(), image.getHeight()));
		int width = (int) Math.round(image.getWidth() * scale);
		int height = (int) Math.round(image.getHeight() * scale);
		BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D graphics = gray.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, width, height);
		graphics.drawImage(image, 0, 0, width, height, null);
		graphics.dispose();
		return gray;
	}
}
