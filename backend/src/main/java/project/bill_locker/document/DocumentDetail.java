package project.bill_locker.document;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * One document with what was read from it (docs/api-contract.md §8,
 * "DocumentDetail"). {@code @JsonUnwrapped} puts the summary's fields at the top
 * level of the JSON. extraction and extractedText stay null until OCR is added.
 */
public record DocumentDetail(@JsonUnwrapped DocumentSummary summary, Object extraction, String extractedText) {

	static DocumentDetail from(Document document) {
		return new DocumentDetail(DocumentSummary.from(document), null, null);
	}
}
