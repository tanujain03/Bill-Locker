package project.bill_locker.document;

/** A stored file, ready to send back to the browser. */
public record DownloadedFile(String fileName, String mimeType, byte[] data) {
}
