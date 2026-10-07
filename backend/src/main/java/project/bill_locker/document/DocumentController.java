package project.bill_locker.document;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import project.bill_locker.security.CurrentUser;

/** HTTP ↔ Java for documents. Every endpoint needs a login token. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	private final DocumentService documentService;

	public DocumentController(DocumentService documentService) {
		this.documentService = documentService;
	}

	/** The browser sends the file as multipart/form-data, in a part called "file". */
	@PostMapping("/upload")
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentDetail upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
		return documentService.upload(CurrentUser.id(jwt), file);
	}

	/** The documents list. All three filters are optional. */
	@GetMapping
	public List<DocumentSummary> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) DocumentType type,
			@RequestParam(required = false) DocumentStatus status) {
		return documentService.list(CurrentUser.id(jwt), q, type, status);
	}

	@GetMapping("/{id}")
	public DocumentDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return documentService.get(CurrentUser.id(jwt), id);
	}

	/** The file itself. "inline" lets the browser show it (preview) instead of saving it. */
	@GetMapping("/{id}/download")
	public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		DownloadedFile file = documentService.download(CurrentUser.id(jwt), id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
				.body(file.data());
	}

	/** Read the details with AI. Can be repeated ("Read again"). */
	@PostMapping("/{id}/extract")
	public DocumentDetail extract(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return documentService.extract(CurrentUser.id(jwt), id);
	}

	/** Save the reviewed details (first time and every later edit). */
	@PutMapping("/{id}")
	public DocumentDetail save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody SaveDocumentRequest request) {
		return documentService.save(CurrentUser.id(jwt), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		documentService.delete(CurrentUser.id(jwt), id);
	}
}
