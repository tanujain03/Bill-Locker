package project.bill_locker.gmail;

/**
 * An attachment of a shortlisted email (stored as JSON). The file itself is only
 * downloaded from Gmail when the user imports the email.
 */
public record GmailAttachment(String fileName, String mimeType, long size, String attachmentId) {
}
