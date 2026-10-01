package project.bill_locker.processing;

/** The text found in a file, and how it was found ("PDF text", "OCR" …), for the logs. */
public record ReadText(String text, String method) {
}
