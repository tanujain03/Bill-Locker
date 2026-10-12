package project.bill_locker.document;

import org.springframework.stereotype.Component;

/** Reads queued documents with the AI, one per call (the timer lives in WorkerSchedule). */
@Component
public class DocumentReadWorker {

	private final DocumentService documents;

	public DocumentReadWorker(DocumentService documents) {
		this.documents = documents;
	}

	/** Returns false when nothing was waiting. */
	public boolean runOnce() {
		return documents.readNextQueued();
	}
}
