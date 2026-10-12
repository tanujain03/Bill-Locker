package project.bill_locker.document.ai;

import java.util.List;
import project.bill_locker.document.DocumentDetails;

/**
 * Reads the details out of a bill's file. The real one asks Gemini
 * ({@link GeminiDetailExtractor}); tests use a fake. Swapping AI providers means
 * writing one new class that implements this.
 */
public interface DetailExtractor {

	/**
	 * @param file        the file's bytes (PDF, JPEG, PNG or WebP)
	 * @param contentType its MIME type, e.g. "application/pdf"
	 * @throws ExtractionException when the details can't be read
	 */
	DocumentDetails extract(byte[] file, String contentType);

	/**
	 * The same, with the links found in the bill's QR codes (QrCodeReader), so the AI can
	 * tell which one registers the warranty. Readers that can't use them just ignore them.
	 */
	default DocumentDetails extract(byte[] file, String contentType, List<String> qrLinks) {
		return extract(file, contentType);
	}
}
