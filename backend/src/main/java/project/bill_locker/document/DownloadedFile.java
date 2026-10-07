package project.bill_locker.document;

/** A stored file on its way back to the browser. */
record DownloadedFile(String fileName, String contentType, byte[] data) {
}
