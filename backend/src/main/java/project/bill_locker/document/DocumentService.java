package project.bill_locker.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.ai.DetailExtractor;
import project.bill_locker.document.ai.ExtractionException;
import project.bill_locker.user.UserRepository;

/** The rules for documents: what may be uploaded, who may see what. */
@Service
public class DocumentService {

	static final long MAX_FILE_BYTES = 10 * 1024 * 1024;

	private final DocumentRepository documents;
	private final DocumentFileRepository files;
	private final UserRepository users;
	private final DetailExtractor extractor;
	private final TransactionTemplate transaction;

	public DocumentService(DocumentRepository documents, DocumentFileRepository files, UserRepository users,
			DetailExtractor extractor, PlatformTransactionManager transactionManager) {
		this.documents = documents;
		this.files = files;
		this.users = users;
		this.extractor = extractor;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Transactional
	public DocumentDetail upload(UUID userId, MultipartFile file) {
		if (file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "The file is empty.");
		}
		if (file.getSize() > MAX_FILE_BYTES) {
			throw fileTooLarge();
		}
		byte[] bytes = readBytes(file);
		String contentType = FileType.detect(bytes).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
				"INVALID_FILE_TYPE", "Please upload a PDF, JPG, PNG or WebP file."));

		// getReferenceById: we only need the user's id for the foreign key, not a SELECT.
		Document document = documents.save(new Document(users.getReferenceById(userId),
				cleanFileName(file.getOriginalFilename()), contentType, bytes.length));
		files.save(new DocumentFile(document, bytes));
		return DocumentDetail.of(document);
	}

	@Transactional(readOnly = true)
	public List<DocumentSummary> list(UUID userId, String q, DocumentType type, DocumentStatus status) {
		// LIKE treats % and _ as wildcards; escaping them isn't worth it for a search box.
		String search = (q == null || q.isBlank()) ? "" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
		return documents.search(userId, type, status, search).stream().map(DocumentSummary::of).toList();
	}

	@Transactional(readOnly = true)
	public DocumentDetail get(UUID userId, UUID id) {
		return DocumentDetail.of(findOwned(userId, id));
	}

	@Transactional(readOnly = true)
	public DownloadedFile download(UUID userId, UUID id) {
		Document document = findOwned(userId, id);
		byte[] data = files.findById(document.getId()).orElseThrow(DocumentService::notFound).getData();
		return new DownloadedFile(document.getFileName(), document.getContentType(), data);
	}

	/** Saves the user's reviewed details. Also used to edit a saved document later. */
	@Transactional
	public DocumentDetail save(UUID userId, UUID id, SaveDocumentRequest request) {
		Document document = findOwned(userId, id);
		DocumentDetails details = request.toDetails();
		checkWarrantyDates(details);
		document.replaceDetails(details, DocumentStatus.SAVED);
		return DocumentDetail.of(document);
	}

	/**
	 * Sends the file to the AI and stores what it read (status EXTRACTED, waiting for
	 * the user's review). Not one big transaction on purpose: the AI can take many
	 * seconds, and a transaction would hold a database connection all that time. So:
	 * 1) read the file, 2) ask the AI (no transaction), 3) store the answer.
	 * If the AI fails, step 3 never runs and the document stays exactly as it was.
	 */
	public DocumentDetail extract(UUID userId, UUID id) {
		DownloadedFile file = transaction.execute(status -> download(userId, id));

		DocumentDetails details;
		try {
			details = withWarrantyEndDates(extractor.extract(file.data(), file.contentType()));
		} catch (ExtractionException e) {
			HttpStatus status = ExtractionException.NOT_CONFIGURED.equals(e.getCode())
					? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
			String message = ExtractionException.NOT_CONFIGURED.equals(e.getCode())
					? "AI reading is not set up. Add GEMINI_API_KEY to backend/.env, or type the details in yourself."
					: e.getMessage();
			throw new ApiException(status, e.getCode(), message);
		}

		return transaction.execute(status -> {
			Document document = findOwned(userId, id); // it may have been deleted meanwhile → 404
			document.replaceDetails(details, DocumentStatus.EXTRACTED);
			return DocumentDetail.of(document);
		});
	}

	@Transactional
	public void delete(UUID userId, UUID id) {
		// The database removes the file and items too (ON DELETE CASCADE).
		documents.delete(findOwned(userId, id));
	}

	/** The user's document, or 404 if it doesn't exist or is someone else's. */
	Document findOwned(UUID userId, UUID id) {
		return documents.findByIdAndUserId(id, userId).orElseThrow(DocumentService::notFound);
	}

	/** A warranty can't end before it starts. Reported under the item's own field. */
	private static void checkWarrantyDates(DocumentDetails details) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (int i = 0; i < details.items().size(); i++) {
			DocumentItemView item = details.items().get(i);
			if (item.warrantyStartDate() != null && item.warrantyEndDate() != null
					&& item.warrantyEndDate().isBefore(item.warrantyStartDate())) {
				errors.put("items[" + i + "].warrantyEndDate", "Must be on or after the start date.");
			}
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please check the highlighted fields.",
					errors);
		}
	}

	/**
	 * Bills often print "Warranty: 12 months" but no end date. Then we work it out:
	 * a 12-month warranty starting 10 Jan 2026 covers up to and including 9 Jan 2027.
	 */
	private static DocumentDetails withWarrantyEndDates(DocumentDetails d) {
		List<DocumentItemView> items = d.items().stream().map(item -> {
			if (item.warrantyEndDate() != null || item.warrantyStartDate() == null
					|| item.warrantyPeriodMonths() == null) {
				return item;
			}
			LocalDate end = item.warrantyStartDate().plusMonths(item.warrantyPeriodMonths()).minusDays(1);
			return new DocumentItemView(item.productName(), item.modelNumber(), item.serialNumber(), item.unitPrice(),
					item.warrantyPeriodMonths(), item.warrantyStartDate(), end, item.warrantyProvider());
		}).toList();
		return new DocumentDetails(d.documentType(), d.documentNumber(), d.sellerName(), d.sellerAddress(),
				d.sellerContact(), d.buyerName(), d.buyerAddress(), d.buyerEmail(), d.purchaseDate(), d.taxAmount(),
				d.totalAmount(), items);
	}

	static ApiException fileTooLarge() {
		return new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Files can be at most 10 MB.");
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "The requested document was not found.");
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Keeps only the name ("C:\scans\bill.pdf" → "bill.pdf"), never a path. */
	private static String cleanFileName(String original) {
		if (original == null || original.isBlank()) {
			return "document";
		}
		String name = original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).strip();
		if (name.isEmpty()) {
			return "document";
		}
		return name.length() > 255 ? name.substring(name.length() - 255) : name;
	}
}
