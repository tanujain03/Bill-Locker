package project.bill_locker.processing;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.bill_locker.document.DocumentService;
import project.bill_locker.document.ExtractionResult;
import project.bill_locker.document.FileToProcess;
import project.bill_locker.document.ProcessingStage;

/**
 * The background reader. Uploading only stores the file (fast); every 2 seconds this
 * worker takes the documents waiting in status UPLOADED and reads them:
 *
 * <pre>
 * UPLOADED → PROCESSING (stage OCR: get the text → stage EXTRACTION: find the details) → PROCESSED
 *                                                                                    ↘ FAILED (errorMessage)
 * </pre>
 *
 * The app shows the progress because it asks for the document every 1.5 seconds.
 */
@Component
public class DocumentProcessor {

	private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

	private final DocumentService documents;
	private final TextReader textReader;
	private final DetailExtractor detailExtractor;
	private final boolean enabled;

	public DocumentProcessor(DocumentService documents, TextReader textReader, DetailExtractor detailExtractor,
			@Value("${app.processing.enabled:true}") boolean enabled) {
		this.documents = documents;
		this.textReader = textReader;
		this.detailExtractor = detailExtractor;
		this.enabled = enabled;
	}

	/** Runs 2 seconds after the previous run has finished. */
	@Scheduled(fixedDelay = 2000, initialDelay = 5000)
	public void onSchedule() {
		if (enabled) {
			processPendingDocuments();
		}
	}

	/** Reads every waiting document, oldest first, and returns how many it handled. */
	public int processPendingDocuments() {
		int handled = 0;
		Optional<FileToProcess> next = documents.startNextProcessing();
		while (next.isPresent()) {
			process(next.get());
			handled++;
			next = documents.startNextProcessing();
		}
		return handled;
	}

	private void process(FileToProcess file) {
		long startedAt = System.currentTimeMillis();
		try {
			ReadText read = textReader.read(file.data(), file.mimeType());
			if (read.text().isBlank()) {
				documents.failProcessing(file.documentId(),
						"No text was found. Try a sharper photo in good light, or the original PDF.");
				return;
			}
			documents.moveToStage(file.documentId(), ProcessingStage.EXTRACTION);
			ExtractionResult details = detailExtractor.extract(read.text());
			documents.finishProcessing(file.documentId(), read.text(), details);
			log.info("Read document {} using {} in {} ms", file.documentId(), read.method(),
					System.currentTimeMillis() - startedAt);
		}
		catch (UnreadableDocumentException ex) {
			documents.failProcessing(file.documentId(), ex.getMessage());
		}
		catch (RuntimeException | LinkageError ex) { // LinkageError: e.g. the OCR program itself could not load
			log.error("Reading document {} failed", file.documentId(), ex);
			documents.failProcessing(file.documentId(), "We couldn't read this document. Please try again.");
		}
	}

	/** When the app starts, documents that were half-read when it stopped go back in the queue. */
	@EventListener(ApplicationReadyEvent.class)
	public void requeueInterrupted() {
		int count = documents.requeueInterrupted();
		if (count > 0) {
			log.info("{} document(s) were being read when the app stopped; they will be read again", count);
		}
	}
}
