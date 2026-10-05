package project.bill_locker.search;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.bill_locker.security.CurrentUser;

/**
 * Plain-English search (docs/api-contract.md §12). The path says "ai" because the
 * contract plans an AI model to understand the question; for now rules do that part.
 */
@RestController
@RequestMapping("/api/ai")
public class SearchController {

	private final SearchService searchService;

	public SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	/** {@code POST /api/ai/search} — e.g. {@code { "query": "what did I buy from Croma?" }}. */
	@PostMapping("/search")
	public SearchResponse search(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SearchRequest request) {
		return searchService.search(CurrentUser.id(jwt), request.query());
	}
}
