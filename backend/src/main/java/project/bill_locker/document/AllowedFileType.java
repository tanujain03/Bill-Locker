package project.bill_locker.document;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * The file types we accept. A file is recognised by its first bytes (its "magic
 * number"), not by its name or by what the browser claims: renaming virus.exe to
 * bill.pdf doesn't make it a PDF.
 */
enum AllowedFileType {

	PDF("application/pdf", "pdf"),
	JPEG("image/jpeg", "jpg", "jpeg"),
	PNG("image/png", "png"),
	WEBP("image/webp", "webp");

	private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
	private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
	private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] WEBP_MARK = "WEBP".getBytes(StandardCharsets.US_ASCII);

	final String mimeType;
	final Set<String> extensions;

	AllowedFileType(String mimeType, String... extensions) {
		this.mimeType = mimeType;
		this.extensions = Set.of(extensions);
	}

	/** The type whose magic number the content starts with, if it is one we accept. */
	static Optional<AllowedFileType> detect(byte[] content) {
		if (startsWith(content, 0, PDF_MAGIC)) {
			return Optional.of(PDF);
		}
		if (startsWith(content, 0, JPEG_MAGIC)) {
			return Optional.of(JPEG);
		}
		if (startsWith(content, 0, PNG_MAGIC)) {
			return Optional.of(PNG);
		}
		if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_MARK)) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] content, int offset, byte[] expected) {
		return content.length >= offset + expected.length
				&& Arrays.equals(content, offset, offset + expected.length, expected, 0, expected.length);
	}
}
