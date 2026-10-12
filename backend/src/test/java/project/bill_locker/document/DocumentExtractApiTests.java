package project.bill_locker.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.FakeDetailExtractor;
import project.bill_locker.document.ai.ExtractionException;

/** "Read with AI", using {@link FakeDetailExtractor} instead of Gemini. */
class DocumentExtractApiTests extends DocumentApiTestBase {

	@Autowired
	private FakeDetailExtractor ai;

	@AfterEach
	void resetAi() {
		ai.reset();
	}

	private ResultActions extract(String token, String id) throws Exception {
		return mvc.perform(post("/api/documents/" + id + "/extract").header("Authorization", bearer(token)));
	}

	@Test
	void extractFillsDetailsAndComputesWarrantyEnd() throws Exception {
		String token = registerAndGetToken(uniqueEmail("read"));
		String id = uploadPdf(token, "bill.pdf");

		extract(token, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EXTRACTED"))
				.andExpect(jsonPath("$.documentType").value("INVOICE"))
				.andExpect(jsonPath("$.documentNumber").value("INV-1029"))
				.andExpect(jsonPath("$.items.length()").value(2))
				// Start 2026-01-10 + 12 months → covered up to and including 2027-01-09.
				.andExpect(jsonPath("$.items[0].warrantyEndDate").value("2027-01-09"));
	}

	@Test
	void extractFailureLeavesDocumentUnchanged() throws Exception {
		String token = registerAndGetToken(uniqueEmail("fail"));
		String id = uploadPdf(token, "bill.pdf");
		mvc.perform(put("/api/documents/" + id).header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sellerName\": \"My shop\", \"items\": [{\"productName\": \"Fan\"}]}"))
				.andExpect(status().isOk());

		ai.willFail(new ExtractionException("EXTRACTION_FAILED", "Gemini could not read this document right now."));
		extract(token, id)
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("EXTRACTION_FAILED"))
				.andExpect(jsonPath("$.message").value("Gemini could not read this document right now."));

		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.status").value("SAVED"))
				.andExpect(jsonPath("$.sellerName").value("My shop"))
				.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void extractWithoutKeyGives503() throws Exception {
		String token = registerAndGetToken(uniqueEmail("nokey"));
		String id = uploadPdf(token, "bill.pdf");

		ai.willFail(new ExtractionException("AI_NOT_CONFIGURED", "not set up"));
		extract(token, id)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
	}

	@Test
	void extractAgainReplacesItems() throws Exception {
		String token = registerAndGetToken(uniqueEmail("again"));
		String id = uploadPdf(token, "bill.pdf");
		extract(token, id).andExpect(jsonPath("$.items.length()").value(2));
		extract(token, id).andExpect(jsonPath("$.items.length()").value(2));

		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.items.length()").value(2));
	}

	@Test
	void otherUserCannotExtract() throws Exception {
		String owner = registerAndGetToken(uniqueEmail("owner"));
		String other = registerAndGetToken(uniqueEmail("other"));
		String id = uploadPdf(owner, "bill.pdf");

		extract(other, id).andExpect(status().isNotFound());
	}
}
