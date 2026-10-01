package project.bill_locker.document;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import project.bill_locker.security.CurrentUser;

/**
 * Document endpoints (docs/api-contract.md §8). Every method needs a valid token;
 * {@code @AuthenticationPrincipal Jwt} is that verified token.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	private final DocumentService documentService;

	public DocumentController(DocumentService documentService) {
		this.documentService = documentService;
	}

	/** {@code POST /api/documents/upload} — multipart form with a "file" and an optional "documentType". */
	@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentSummary upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file,
			@RequestParam(required = false) DocumentType documentType) {
		return documentService.upload(CurrentUser.id(jwt), file, documentType);
	}

	/** {@code GET /api/documents?status=&documentType=} — the user's documents, newest first. */
	@GetMapping
	public List<DocumentSummary> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) UUID productId,
			@RequestParam(required = false) ProcessingStatus status,
			@RequestParam(required = false) DocumentType documentType) {
		return documentService.list(CurrentUser.id(jwt), productId, status, documentType);
	}

	/** {@code GET /api/documents/{id}} */
	@GetMapping("/{id}")
	public DocumentDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return documentService.get(CurrentUser.id(jwt), id);
	}

	/** {@code GET /api/documents/{id}/download} — the file itself; "inline" lets the browser show it. */
	@GetMapping("/{id}/download")
	public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		DownloadedFile file = documentService.download(CurrentUser.id(jwt), id);
		ContentDisposition disposition = ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.mimeType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(file.data());
	}

	/** {@code POST /api/documents/{id}/reprocess} — read the document again, e.g. after a failure. */
	@PostMapping("/{id}/reprocess")
	public DocumentSummary reprocess(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return documentService.reprocess(CurrentUser.id(jwt), id);
	}

	/** {@code DELETE /api/documents/{id}} — removes the document and its file. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		documentService.delete(CurrentUser.id(jwt), id);
	}
}
