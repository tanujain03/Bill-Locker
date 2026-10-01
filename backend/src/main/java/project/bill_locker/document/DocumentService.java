package project.bill_locker.document;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.bill_locker.common.ApiException;
import project.bill_locker.user.User;
import project.bill_locker.user.UserService;

/**
 * Storing and reading documents. Every method takes the signed-in user's id and
 * only ever touches that user's documents.
 */
@Service
public class DocumentService {

	private final DocumentRepository documents;
	private final DocumentFileRepository documentFiles;
	private final UserService userService;

	public DocumentService(DocumentRepository documents, DocumentFileRepository documentFiles, UserService userService) {
		this.documents = documents;
		this.documentFiles = documentFiles;
		this.userService = userService;
	}

	/**
	 * Checks the file and saves it: one row in {@code documents} (the details) and one
	 * in {@code document_files} (the bytes). Both are written in one transaction, so
	 * either both are saved or neither.
	 */
	@Transactional
	public DocumentSummary upload(UUID userId, MultipartFile file, DocumentType documentType) {
		if (file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "The file is empty. Choose another file.");
		}
		byte[] content = readContent(file);
		String fileName = cleanFileName(file.getOriginalFilename());
		AllowedFileType type = AllowedFileType.detect(content)
				.filter(detected -> detected.extensions.contains(extensionOf(fileName)))
				.orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
						"Only PDF, JPG, PNG and WEBP files are supported."));

		User owner = userService.findUser(userId);
		Document document = documents.save(new Document(owner, fileName, type.mimeType, content.length,
				documentType != null ? documentType : DocumentType.OTHER));
		documentFiles.save(new DocumentFile(document, content));
		return DocumentSummary.from(document);
	}

	@Transactional(readOnly = true)
	public List<DocumentSummary> list(UUID userId, UUID productId, ProcessingStatus status, DocumentType documentType) {
		if (productId != null) {
			return List.of(); // products come in a later step, so no document is linked to one yet
		}
		return documents.findForUser(userId, status, documentType).stream().map(DocumentSummary::from).toList();
	}

	@Transactional(readOnly = true)
	public DocumentDetail get(UUID userId, UUID documentId) {
		return DocumentDetail.from(findOwned(userId, documentId));
	}

	@Transactional(readOnly = true)
	public DownloadedFile download(UUID userId, UUID documentId) {
		Document document = findOwned(userId, documentId);
		DocumentFile file = documentFiles.findById(document.getId()).orElseThrow(DocumentService::notFound);
		return new DownloadedFile(document.getFileName(), document.getMimeType(), file.getData());
	}

	@Transactional
	public void delete(UUID userId, UUID documentId) {
		// PostgreSQL removes the matching document_files row by itself (ON DELETE CASCADE).
		documents.delete(findOwned(userId, documentId));
	}

	/** Puts a read (or failed) document back in the queue, so it is read again. */
	@Transactional
	public DocumentSummary reprocess(UUID userId, UUID documentId) {
		Document document = findOwned(userId, documentId);
		ProcessingStatus status = document.getProcessingStatus();
		if (status == ProcessingStatus.UPLOADED || status == ProcessingStatus.PROCESSING) {
			throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_READY", "This document is still being read.");
		}
		document.queueForProcessing();
		return DocumentSummary.from(document);
	}

	// ---- Used by the background reader (processing/DocumentProcessor) ----
	// Each method is one short transaction, so no database lock is held while OCR runs.

	/** Takes the oldest UPLOADED document, marks it PROCESSING and hands over its file. */
	@Transactional
	public Optional<FileToProcess> startNextProcessing() {
		return documents.findFirstByProcessingStatusOrderByCreatedAtAsc(ProcessingStatus.UPLOADED).map(document -> {
			document.startProcessing();
			byte[] data = documentFiles.findById(document.getId()).map(DocumentFile::getData).orElse(new byte[0]);
			return new FileToProcess(document.getId(), document.getMimeType(), data);
		});
	}

	// The document may have been deleted while it was being read: then there is nothing to update.

	@Transactional
	public void moveToStage(UUID documentId, ProcessingStage stage) {
		documents.findById(documentId).ifPresent(document -> document.moveToStage(stage));
	}

	@Transactional
	public void finishProcessing(UUID documentId, String text, ExtractionResult details) {
		documents.findById(documentId).ifPresent(document -> document.finishProcessing(text, details));
	}

	@Transactional
	public void failProcessing(UUID documentId, String reason) {
		documents.findById(documentId).ifPresent(document -> document.failProcessing(reason));
	}

	/** Documents that were half-read when the app stopped go back in the queue. */
	@Transactional
	public int requeueInterrupted() {
		List<Document> interrupted = documents.findByProcessingStatus(ProcessingStatus.PROCESSING);
		interrupted.forEach(Document::queueForProcessing);
		return interrupted.size();
	}

	/** Someone else's document looks exactly like a missing one (404), so ids reveal nothing. */
	private Document findOwned(UUID userId, UUID documentId) {
		return documents.findByIdAndUserId(documentId, userId).orElseThrow(DocumentService::notFound);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "The requested document was not found.");
	}

	private static byte[] readContent(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", "The upload could not be read. Please try again.");
		}
	}

	/** Keeps only the name ("C:\fakepath\bill.pdf" becomes "bill.pdf"), without control characters. */
	static String cleanFileName(String originalName) {
		String name = originalName == null ? "" : originalName;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("\\p{Cntrl}", "").trim();
		if (name.length() > 255) {
			name = name.substring(name.length() - 255); // keep the end, where the extension is
		}
		return name;
	}

	private static String extensionOf(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
