package project.bill_locker.document.ai;

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
}
