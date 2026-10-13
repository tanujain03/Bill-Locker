package project.bill_locker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Duplicate bills: the same file twice (refused) and two files of one bill (warned). */
class DocumentDuplicateApiTests extends DocumentApiTestBase {

	private static final String CROMA = """
			{"documentType": "INVOICE", "documentNumber": "INV-77", "sellerName": "Croma",
			 "purchaseDate": "2026-03-01", "totalAmount": 1499.00, "items": []}
			""";

	@Test
	void sameFileTwiceIsRefusedWithTheEarlierDocument() throws Exception {
		String token = registerAndGetToken(uniqueEmail("dup-file"));
		byte[] file = pdfBytes();
		String body = upload(token, "bill.pdf", "application/pdf", file)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String firstId = JsonPath.read(body, "$.id");

		upload(token, "bill (1).pdf", "application/pdf", file)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_FILE"))
				.andExpect(jsonPath("$.documentId").value(firstId));
	}

	@Test
	void anotherUserMayUploadTheSameFile() throws Exception {
		byte[] file = pdfBytes();
		upload(registerAndGetToken(uniqueEmail("dup-a")), "bill.pdf", "application/pdf", file)
				.andExpect(status().isCreated());
		upload(registerAndGetToken(uniqueEmail("dup-b")), "bill.pdf", "application/pdf", file)
				.andExpect(status().isCreated());
	}

	@Test
	void sameNumberAndDateIsAPossibleCopy() throws Exception {
		String token = registerAndGetToken(uniqueEmail("dup-number"));
		String first = saveBill(token, CROMA);
		// A photo of the same bill, read a little differently.
		String second = saveBill(token, """
				{"documentNumber": "inv 77", "sellerName": "CROMA", "purchaseDate": "2026-03-01", "items": []}
				""");

		mvc.perform(get("/api/documents/" + second).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.possibleDuplicates.length()").value(1))
				.andExpect(jsonPath("$.possibleDuplicates[0].id").value(first))
				.andExpect(jsonPath("$.possibleDuplicates[0].sellerName").value("Croma"));
		mvc.perform(get("/api/documents/" + first).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.possibleDuplicates[0].id").value(second));
	}

	@Test
	void sameShopDateAndTotalIsAPossibleCopy() throws Exception {
		String token = registerAndGetToken(uniqueEmail("dup-total"));
		saveBill(token, CROMA);
		String second = saveBill(token, """
				{"sellerName": "Croma", "purchaseDate": "2026-03-01", "totalAmount": 1499, "items": []}
				""");

		mvc.perform(get("/api/documents/" + second).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.possibleDuplicates.length()").value(1));
	}

	@Test
	void differentBillsAreNotCopies() throws Exception {
		String token = registerAndGetToken(uniqueEmail("dup-none"));
		saveBill(token, CROMA);
		// Same shop and day, other amount; same number, other day; same number, other shop.
		String[] others = {
				"""
				{"sellerName": "Croma", "purchaseDate": "2026-03-01", "totalAmount": 99, "items": []}
				""",
				"""
				{"documentNumber": "INV-77", "sellerName": "Croma", "purchaseDate": "2026-03-02", "items": []}
				""",
				"""
				{"documentNumber": "INV-77", "sellerName": "Reliance", "purchaseDate": "2026-03-01", "items": []}
				"""};
		for (String json : others) {
			String id = saveBill(token, json);
			mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
					.andExpect(jsonPath("$.possibleDuplicates.length()").value(0));
		}
	}

	@Test
	void anotherUsersBillIsNeverACopy() throws Exception {
		saveBill(registerAndGetToken(uniqueEmail("dup-other-a")), CROMA);
		String token = registerAndGetToken(uniqueEmail("dup-other-b"));
		String id = saveBill(token, CROMA);

		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.possibleDuplicates.length()").value(0));
	}

	@Test
	void listMarksPossibleCopies() throws Exception {
		String token = registerAndGetToken(uniqueEmail("dup-list"));
		saveBill(token, CROMA);
		saveBill(token, """
				{"sellerName": "Croma", "purchaseDate": "2026-03-01", "totalAmount": 1499, "items": []}
				""");
		saveBill(token, """
				{"sellerName": "Uber", "purchaseDate": "2026-03-01", "totalAmount": 120, "items": []}
				""");

		String body = mvc.perform(get("/api/documents").header("Authorization", bearer(token)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Boolean> flags = JsonPath.read(body, "$[*].possibleDuplicate");
		assertThat(flags).containsExactlyInAnyOrder(true, true, false);

		// A search that finds only one of the two copies still marks it.
		mvc.perform(get("/api/documents?q=inv-77").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].possibleDuplicate").value(true));
	}

	@Test
	void keepBothStopsTheWarning() throws Exception {
		String token = registerAndGetToken(uniqueEmail("dup-keep"));
		String first = saveBill(token, CROMA);
		String second = saveBill(token, CROMA);

		mvc.perform(post("/api/documents/" + second + "/not-duplicate").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.possibleDuplicates.length()").value(0));
		// The other bill is not warned about it either.
		mvc.perform(get("/api/documents/" + first).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.possibleDuplicates.length()").value(0));
	}

	@Test
	void keepBothOnAnotherUsersDocumentIs404() throws Exception {
		String id = saveBill(registerAndGetToken(uniqueEmail("dup-404-a")), CROMA);
		String token = registerAndGetToken(uniqueEmail("dup-404-b"));

		mvc.perform(post("/api/documents/" + id + "/not-duplicate").header("Authorization", bearer(token)))
				.andExpect(status().isNotFound());
	}
}
