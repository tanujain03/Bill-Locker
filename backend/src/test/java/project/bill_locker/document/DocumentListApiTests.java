package project.bill_locker.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** The documents list: only your own, newest first, with search and filters. */
class DocumentListApiTests extends DocumentApiTestBase {

	@Test
	void listShowsOnlyMyDocumentsNewestFirst() throws Exception {
		String me = registerAndGetToken(uniqueEmail("me"));
		String other = registerAndGetToken(uniqueEmail("other"));
		uploadPdf(me, "first.pdf");
		uploadPdf(me, "second.pdf");
		uploadPdf(other, "not-mine.pdf");

		mvc.perform(get("/api/documents").header("Authorization", bearer(me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].fileName").value("second.pdf"))
				.andExpect(jsonPath("$[1].fileName").value("first.pdf"))
				.andExpect(jsonPath("$[0].status").value("UPLOADED"))
				.andExpect(jsonPath("$[0].itemCount").value(0));
	}

	@Test
	void listFiltersByStatus() throws Exception {
		String token = registerAndGetToken(uniqueEmail("status"));
		uploadPdf(token, "a.pdf");
		uploadPdf(token, "b.pdf");

		mvc.perform(get("/api/documents?status=UPLOADED").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.length()").value(2));
		mvc.perform(get("/api/documents?status=SAVED").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void searchMatchesFileNameCaseInsensitive() throws Exception {
		String token = registerAndGetToken(uniqueEmail("search"));
		uploadPdf(token, "amazon-bill.pdf");
		uploadPdf(token, "flipkart.pdf");

		mvc.perform(get("/api/documents?q=AMAZON").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].fileName").value("amazon-bill.pdf"));
	}
}
