package project.bill_locker.document;

import java.util.Optional;

/**
 * Works out what a file really is from its first bytes (its "magic number"), not
 * from its name or the type the browser claims: both are easy to fake.
 */
final class FileType {

	private FileType() {
	}

	/** The file's MIME type if it is a PDF, JPEG, PNG or WebP; empty otherwise. */
	static Optional<String> detect(byte[] bytes) {
		if (startsWith(bytes, 0, 0x25, 0x50, 0x44, 0x46)) { // %PDF
			return Optional.of("application/pdf");
		}
		if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
			return Optional.of("image/jpeg");
		}
		if (startsWith(bytes, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) { // .PNG....
			return Optional.of("image/png");
		}
		if (startsWith(bytes, 0, 0x52, 0x49, 0x46, 0x46) && startsWith(bytes, 8, 0x57, 0x45, 0x42, 0x50)) { // RIFF....WEBP
			return Optional.of("image/webp");
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] bytes, int offset, int... expected) {
		if (bytes.length < offset + expected.length) {
			return false;
		}
		for (int i = 0; i < expected.length; i++) {
			if ((bytes[offset + i] & 0xFF) != expected[i]) {
				return false;
			}
		}
		return true;
	}
}
