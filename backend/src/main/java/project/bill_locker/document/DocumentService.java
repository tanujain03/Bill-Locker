package project.bill_locker.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.ai.DetailExtractor;
import project.bill_locker.document.ai.ExtractionException;
import project.bill_locker.document.registration.QrCodeReader;
import project.bill_locker.document.registration.RegistrationFinder;
import project.bill_locker.user.UserRepository;
import project.bill_locker.warranty.WarrantyRules;

/** The rules for documents: what may be uploaded, who may see what. */
@Service
public class DocumentService {

	static final long MAX_FILE_BYTES = 10 * 1024 * 1024;

	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

	private static final String AI_NOT_SET_UP =
			"AI reading is not set up. Add GEMINI_API_KEY to backend/.env, or type the details in yourself.";

	private final DocumentRepository documents;
	private final DocumentFileRepository files;
	private final UserRepository users;
	private final DetailExtractor extractor;
	private final QrCodeReader qrCodes;
	private final RegistrationFinder registrationFinder;
	private final TransactionTemplate transaction;

	public DocumentService(DocumentRepository documents, DocumentFileRepository files, UserRepository users,
			DetailExtractor extractor, QrCodeReader qrCodes, RegistrationFinder registrationFinder,
			PlatformTransactionManager transactionManager) {
		this.documents = documents;
		this.files = files;
		this.users = users;
		this.extractor = extractor;
		this.qrCodes = qrCodes;
		this.registrationFinder = registrationFinder;
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
		return DocumentDetail.of(create(userId, file.getOriginalFilename(), readBytes(file), null));
	}

	/**
	 * Stores a file that did not come from the upload form (the Gmail import) and queues
	 * it for the background AI reading. Same checks as {@link #upload}.
	 */
	@Transactional
	public DocumentDetail createFromBytes(UUID userId, String fileName, byte[] bytes, String sourceGmail) {
		if (bytes.length == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "The file is empty.");
		}
		if (bytes.length > MAX_FILE_BYTES) {
			throw fileTooLarge();
		}
		Document document = create(userId, fileName, bytes, sourceGmail);
		document.queueForReading();
		return DocumentDetail.of(document);
	}

	private Document create(UUID userId, String fileName, byte[] bytes, String sourceGmail) {
		String contentType = FileType.detect(bytes).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
				"INVALID_FILE_TYPE", "Please upload a PDF, JPG, PNG or WebP file."));

		// getReferenceById: we only need the user's id for the foreign key, not a SELECT.
		Document document = documents.save(new Document(users.getReferenceById(userId),
				cleanFileName(fileName), contentType, bytes.length, sourceGmail));
		files.save(new DocumentFile(document, bytes));
		return document;
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
		checkWarrantyDates(details); // checks what the user typed, before we fill anything in
		document.replaceDetails(withWarrantyEndDates(details), DocumentStatus.SAVED);
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
			details = read(file.data(), file.contentType());
		} catch (ExtractionException e) {
			HttpStatus status = ExtractionException.NOT_CONFIGURED.equals(e.getCode())
					? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
			String message = readErrorText(e);
			throw new ApiException(status, e.getCode(), message);
		}

		return transaction.execute(status -> {
			Document document = findOwned(userId, id); // it may have been deleted meanwhile → 404
			document.replaceDetails(details, DocumentStatus.EXTRACTED);
			return DocumentDetail.of(document);
		});
	}

	/**
	 * Reads the document that has waited longest in the queue (same three steps as
	 * {@link #extract}: no transaction around the slow AI call). Returns false when
	 * nothing is queued. A failure is stored on the document, never thrown, so one bad
	 * file cannot block the queue.
	 */
	public boolean readNextQueued() {
		record Job(UUID id, Instant queuedAt, byte[] data, String contentType) {
		}
		Job job = transaction.execute(status -> documents.findFirstByReadQueuedAtIsNotNullOrderByReadQueuedAtAsc()
				.map(d -> new Job(d.getId(), d.getReadQueuedAt(),
						files.findById(d.getId()).map(DocumentFile::getData).orElse(null), d.getContentType()))
				.orElse(null));
		if (job == null) {
			return false;
		}

		if (job.data() == null) { // file row missing: nothing to read, so don't retry forever
			markReadFailed(job.id(), job.queuedAt(), "The file is missing, so it cannot be read.");
			return true;
		}

		DocumentDetails details = null;
		String error = null;
		try {
			details = read(job.data(), job.contentType());
		} catch (ExtractionException e) {
			error = readErrorText(e);
		} catch (RuntimeException e) {
			log.warn("Reading document {} failed", job.id(), e);
			error = "Reading failed. Press Read with AI to try again.";
		}

		try {
			storeRead(job.id(), job.queuedAt(), details, error);
		} catch (RuntimeException e) {
			// e.g. the AI's text does not fit a column: without this the same document would
			// be picked (and sent to the AI) again every tick, blocking everything behind it.
			log.warn("Storing the reading of document {} failed", job.id(), e);
			markReadFailed(job.id(), job.queuedAt(), "The details could not be stored. Press Read with AI to try again.");
		}
		return true;
	}

	/**
	 * One reading of a bill: QR codes first (exact links), then the AI with those links as a
	 * hint, then the rules (warranty end dates, where each registration link came from).
	 */
	private DocumentDetails read(byte[] data, String contentType) {
		List<String> qrLinks = qrCodes.links(data, contentType);
		return withRegistrationSources(withWarrantyEndDates(extractor.extract(data, contentType, qrLinks)), qrLinks);
	}

	/**
	 * A link the AI copied from a QR code is marked QR_CODE, any other one DOCUMENT
	 * (printed). If the AI missed it, a QR link that says "warranty"/"register" in its
	 * address still goes to the products that have no link.
	 */
	static DocumentDetails withRegistrationSources(DocumentDetails d, List<String> qrLinks) {
		String registrationQr = qrLinks.stream()
				.filter(link -> link.toLowerCase(Locale.ROOT).matches(".*(warrant|regist).*"))
				.findFirst().orElse(null);
		List<DocumentItemView> items = d.items().stream().map(item -> {
			if (item.registrationUrl() != null) {
				return item.withRegistration(item.registrationUrl(),
						qrLinks.contains(item.registrationUrl()) ? RegistrationSource.QR_CODE : RegistrationSource.DOCUMENT);
			}
			return registrationQr == null ? item : item.withRegistration(registrationQr, RegistrationSource.QR_CODE);
		}).toList();
		return new DocumentDetails(d.documentType(), d.documentNumber(), d.sellerName(), d.sellerAddress(),
				d.sellerContact(), d.buyerName(), d.buyerAddress(), d.buyerEmail(), d.purchaseDate(), d.taxAmount(),
				d.totalAmount(), items);
	}

	/**
	 * The edge case of task 5: the bill has no registration link and no QR code. The page
	 * showed the brand it detected, the user confirmed it (or corrected it), and now we find
	 * that brand's official registration page (POST /api/documents/{id}/registration-page).
	 * The web search is slow, so it runs outside any transaction (like {@link #extract}).
	 * The link is also kept on the product, so next time it's simply a link.
	 */
	public RegistrationPage findRegistrationPage(UUID userId, UUID id, RegistrationPageRequest request) {
		String productName = transaction.execute(status -> item(findOwned(userId, id), request.position()).getProductName());

		RegistrationPage page = lookUp(request.brand().strip(), productName);

		transaction.executeWithoutResult(status -> {
			Document document = findOwned(userId, id); // it may have been deleted meanwhile → 404
			document.getItems().stream()
					// Still the same product (the user may have saved changes meanwhile).
					.filter(item -> item.getPosition() == request.position()
							&& Objects.equals(item.getProductName(), productName))
					.findFirst()
					.ifPresent(item -> item.setRegistration(page.url(), page.source()));
		});
		return page;
	}

	private static DocumentItem item(Document document, int position) {
		return document.getItems().stream()
				.filter(item -> item.getPosition() == position)
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND",
						"That product is not on this document. Save the bill first."));
	}

	/** The brand's official page if the web search finds one that opens; otherwise a Google search to start from. */
	private RegistrationPage lookUp(String brand, String productName) {
		Optional<String> page;
		try {
			page = registrationFinder.find(brand, productName);
		} catch (RuntimeException e) {
			log.warn("Registration search for {} failed", brand, e);
			page = Optional.empty();
		}
		return page.map(url -> new RegistrationPage(url, RegistrationSource.WEB_SEARCH))
				.orElseGet(() -> new RegistrationPage("https://www.google.com/search?q="
						+ URLEncoder.encode(brand + " warranty registration", StandardCharsets.UTF_8),
						RegistrationSource.SEARCH));
	}

	private void storeRead(UUID id, Instant queuedAt, DocumentDetails details, String error) {
		transaction.executeWithoutResult(status -> documents.findById(id)
				// Deleted, or the user saved/read it meanwhile (queue time changed): leave it alone.
				.filter(d -> queuedAt.equals(d.getReadQueuedAt()))
				.ifPresent(d -> {
					if (details != null) {
						d.replaceDetails(details, DocumentStatus.EXTRACTED);
					} else {
						d.readFailed(error);
					}
				}));
	}

	/** Takes the document out of the queue with a reason (own transaction, so it can follow a failed one). */
	private void markReadFailed(UUID id, Instant queuedAt, String message) {
		storeRead(id, queuedAt, null, message);
	}

	/** The message the user sees when reading fails (a missing key gets a how-to-fix hint). */
	private static String readErrorText(ExtractionException e) {
		return ExtractionException.NOT_CONFIGURED.equals(e.getCode()) ? AI_NOT_SET_UP : e.getMessage();
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
	 * Bills often print "Warranty: 12 months" but no end date. Then we work it out
	 * (WarrantyRules: a 12-month warranty starting 10 Jan 2026 covers up to and
	 * including 9 Jan 2027). Runs on AI reads and on save, so a typed-in bill gets
	 * its end date too.
	 */
	private static DocumentDetails withWarrantyEndDates(DocumentDetails d) {
		List<DocumentItemView> items = d.items().stream().map(item -> {
			LocalDate end = WarrantyRules.effectiveEnd(item.warrantyEndDate(), item.warrantyStartDate(),
					item.warrantyPeriodMonths());
			if (end == null || end.equals(item.warrantyEndDate())) {
				return item;
			}
			return item.withWarrantyEndDate(end);
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
