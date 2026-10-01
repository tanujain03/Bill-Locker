package project.bill_locker.document;

import java.util.UUID;

/** A document handed to the background reader: its id, type and file bytes. */
public record FileToProcess(UUID documentId, String mimeType, byte[] data) {
}
