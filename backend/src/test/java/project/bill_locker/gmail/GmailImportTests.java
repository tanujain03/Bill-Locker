package project.bill_locker.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.document.DocumentReadWorker;
import project.bill_locker.gmail.GoogleApi.Attachment;
import project.bill_locker.gmail.GoogleApi.Email;

class GmailImportTests extends GmailApiTestBase {

	private static final String GMAIL = "/api/integrations/gmail";
	private static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes(StandardCharsets.US_ASCII);

	@Autowired
	GmailScanWorker scanWorker;

	@Autowired
	GmailImportWorker importWorker;

	@Autowired
	DocumentReadWorker documentReadWorker;

	/** The database is shared: leftover scans, imports and queued documents of other tests must not be picked first. */
	@BeforeEach
	@AfterEach
	void drainQueues() {
		while (scanWorker.runOnce()) {
			// each call scans one waiting account
		}
		while (importWorker.runOnce() > 0) {
			// leftovers end FAILED or IMPORTED
		}
		while (documentReadWorker.runOnce()) {
			// read what imports queued
		}
	}

	private void add(String address, String id, String subject, Instant at, String name, byte[] bytes) {
		Attachment file = new Attachment("1", "att-" + id, name, "application/pdf", 50 * 1024, false);
		google.addEmail(address, new Email(id, "Shop <shop@example.com>", subject, "preview", at, List.of(file)),
				Map.of("att-" + id, bytes));
	}

	private ResultActions view(String token, String view) throws Exception {
		return mvc.perform(get(GMAIL + "/emails?view=" + view).header("Authorization", bearer(token)));
	}

	private String viewBody(String token, String view) throws Exception {
		return view(token, view).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private ResultActions act(String token, String action, List<String> ids) throws Exception {
		String json = "{\"fileIds\":[" + String.join(",", ids.stream().map(i -> "\"" + i + "\"").toList()) + "]}";
		return mvc.perform(post(GMAIL + "/files/" + action).header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String firstFileId(String token, String view) throws Exception {
		return JsonPath.read(viewBody(token, view), "$[0].files[0].id");
	}

	private List<String> allFileIds(String token, String view) throws Exception {
		return JsonPath.read(viewBody(token, view), "$[*].files[*].id");
	}

	/** Connects, adds one invoice email, scans; returns the user's token. */
	private String oneInvoice(String address, String name) throws Exception {
		String token = registerAndGetToken(uniqueEmail(name));
		add(address, "m1", "Your invoice", Instant.now().minusSeconds(10), "invoice.pdf", PDF);
		connect(token, address);
		scanWorker.runOnce();
		return token;
	}

	private int documentCount(String token) throws Exception {
		String body = mvc.perform(get("/api/documents").header("Authorization", bearer(token))).andReturn()
				.getResponse().getContentAsString();
		return JsonPath.<List<Object>>read(body, "$").size();
	}

	@Test
	void toReviewListsNewFilesNewestFirst() throws Exception {
		String token = registerAndGetToken(uniqueEmail("imp1"));
		add("imp1@gmail.com", "old", "Old invoice", Instant.now().minusSeconds(100), "old.pdf", PDF);
		add("imp1@gmail.com", "new", "New invoice", Instant.now().minusSeconds(10), "new.pdf", PDF);
		connect(token, "imp1@gmail.com");
		scanWorker.runOnce();

		view(token, "TO_REVIEW").andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].subject").value("New invoice"))
				.andExpect(jsonPath("$[0].accountEmail").value("imp1@gmail.com"))
				.andExpect(jsonPath("$[1].subject").value("Old invoice"))
				.andExpect(jsonPath("$[0].files[0].status").value("NEW"));
		mvc.perform(get(GMAIL).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.counts.toReview").value(2))
				.andExpect(jsonPath("$.counts.imported").value(0));
		view(token, "IMPORTED").andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unknownViewIs400() throws Exception {
		String token = registerAndGetToken(uniqueEmail("imp0"));
		view(token, "NOPE").andExpect(status().isBadRequest());
	}

	@Test
	void importCreatesQueuedDocument() throws Exception {
		String token = oneInvoice("imp2@gmail.com", "imp2");
		String fileId = firstFileId(token, "TO_REVIEW");

		act(token, "import", List.of(fileId)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("IMPORTING"));
		assertThat(importWorker.runOnce()).isEqualTo(1);

		String body = viewBody(token, "IMPORTED");
		assertThat(JsonPath.<String>read(body, "$[0].files[0].status")).isEqualTo("IMPORTED");
		assertThat(JsonPath.<Boolean>read(body, "$[0].files[0].document.readQueued")).isTrue();
		String documentId = JsonPath.read(body, "$[0].files[0].document.id");
		mvc.perform(get("/api/documents/" + documentId).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.sourceGmail").value("imp2@gmail.com"))
				.andExpect(jsonPath("$.fileName").value("invoice.pdf"));
		view(token, "TO_REVIEW").andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get(GMAIL).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.counts.toReview").value(0))
				.andExpect(jsonPath("$.counts.imported").value(1));
	}

	@Test
	void importedDocumentIsReadInBackground() throws Exception {
		String token = oneInvoice("imp3@gmail.com", "imp3");
		act(token, "import", List.of(firstFileId(token, "TO_REVIEW"))).andExpect(status().isOk());
		importWorker.runOnce();
		String documentId = JsonPath.read(viewBody(token, "IMPORTED"), "$[0].files[0].document.id");

		assertThat(documentReadWorker.runOnce()).isTrue();

		mvc.perform(get("/api/documents/" + documentId).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.status").value("EXTRACTED"))
				.andExpect(jsonPath("$.readQueued").value(false));
		view(token, "IMPORTED").andExpect(jsonPath("$[0].files[0].document.status").value("EXTRACTED"))
				.andExpect(jsonPath("$[0].files[0].document.readQueued").value(false));
	}

	@Test
	void importingTwiceCreatesOneDocument() throws Exception {
		String token = oneInvoice("imp4@gmail.com", "imp4");
		String fileId = firstFileId(token, "TO_REVIEW");

		act(token, "import", List.of(fileId)).andExpect(status().isOk());
		act(token, "import", List.of(fileId)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("IMPORTING"));
		assertThat(importWorker.runOnce()).isEqualTo(1);

		assertThat(documentCount(token)).isEqualTo(1);
	}

	@Test
	void fakePdfFails() throws Exception {
		String token = registerAndGetToken(uniqueEmail("imp5"));
		add("imp5@gmail.com", "m1", "Your invoice", Instant.now().minusSeconds(10), "x.pdf",
				"hello".getBytes(StandardCharsets.US_ASCII));
		connect(token, "imp5@gmail.com");
		scanWorker.runOnce();
		act(token, "import", List.of(firstFileId(token, "TO_REVIEW"))).andExpect(status().isOk());

		importWorker.runOnce();

		view(token, "TO_REVIEW").andExpect(jsonPath("$[0].files[0].status").value("FAILED"))
				.andExpect(jsonPath("$[0].files[0].error").value("This file isn't a PDF or image Bill Locker can read."));
		assertThat(documentCount(token)).isZero();
	}

	@Test
	void fileGoneFromGmailFails() throws Exception {
		String token = registerAndGetToken(uniqueEmail("imp6"));
		add("imp6@gmail.com", "gone", "Gone invoice", Instant.now().minusSeconds(20), "gone.pdf", PDF);
		add("imp6@gmail.com", "kept", "Kept invoice", Instant.now().minusSeconds(10), "kept.pdf", PDF);
		connect(token, "imp6@gmail.com");
		scanWorker.runOnce();
		act(token, "import", allFileIds(token, "TO_REVIEW")).andExpect(status().isOk());
		google.removeEmail("imp6@gmail.com", "gone");

		assertThat(importWorker.runOnce()).isEqualTo(2);

		view(token, "TO_REVIEW").andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].files[0].fileName").value("gone.pdf"))
				.andExpect(jsonPath("$[0].files[0].status").value("FAILED"))
				.andExpect(jsonPath("$[0].files[0].error").value("This file is no longer in Gmail."));
		view(token, "IMPORTED").andExpect(jsonPath("$[0].files[0].fileName").value("kept.pdf"));
	}

	@Test
	void revokedAccessFailsFileAndAccount() throws Exception {
		String token = oneInvoice("imp7@gmail.com", "imp7");
		act(token, "import", List.of(firstFileId(token, "TO_REVIEW"))).andExpect(status().isOk());
		google.failNext(new GoogleApi.GoogleApiException("revoked", GoogleApi.GoogleApiException.Kind.REVOKED, null));

		importWorker.runOnce();

		view(token, "TO_REVIEW").andExpect(jsonPath("$[0].files[0].error")
				.value("Gmail access was removed. Connect this account again."));
		mvc.perform(get(GMAIL).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.accounts[0].scanStatus").value("ERROR"));
	}

	@Test
	void unavailableLeavesFileImporting() throws Exception {
		String token = oneInvoice("imp8@gmail.com", "imp8");
		act(token, "import", List.of(firstFileId(token, "TO_REVIEW"))).andExpect(status().isOk());
		google.failNext(new GoogleApi.GoogleApiException("down", GoogleApi.GoogleApiException.Kind.UNAVAILABLE, null));

		assertThat(importWorker.runOnce()).isZero();
		view(token, "TO_REVIEW").andExpect(jsonPath("$[0].files[0].status").value("IMPORTING"));

		assertThat(importWorker.runOnce()).isEqualTo(1); // retried on the next run
		view(token, "IMPORTED").andExpect(jsonPath("$[0].files[0].status").value("IMPORTED"));
	}

	@Test
	void ignoreRestoreImport() throws Exception {
		String token = oneInvoice("imp9@gmail.com", "imp9");
		String fileId = firstFileId(token, "TO_REVIEW");

		act(token, "ignore", List.of(fileId)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("IGNORED"));
		view(token, "IGNORED").andExpect(jsonPath("$.length()").value(1));
		view(token, "TO_REVIEW").andExpect(jsonPath("$.length()").value(0));

		act(token, "restore", List.of(fileId)).andExpect(jsonPath("$[0].status").value("NEW"));
		act(token, "ignore", List.of(fileId)).andExpect(jsonPath("$[0].status").value("IGNORED"));
		act(token, "import", List.of(fileId)).andExpect(jsonPath("$[0].status").value("IMPORTING"));
		assertThat(importWorker.runOnce()).isEqualTo(1);
		view(token, "IMPORTED").andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void ignoredFileIsNotOfferedAgainByScan() throws Exception {
		String token = registerAndGetToken(uniqueEmail("imp10"));
		add("imp10@gmail.com", "m1", "Your invoice", Instant.now().minusSeconds(10), "invoice.pdf", PDF);
		String accountId = connect(token, "imp10@gmail.com");
		scanWorker.runOnce();
		act(token, "ignore", List.of(firstFileId(token, "TO_REVIEW"))).andExpect(status().isOk());

		mvc.perform(post(GMAIL + "/accounts/" + accountId + "/scan").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"range\":\"ONE_YEAR\"}"))
				.andExpect(status().isOk());
		scanWorker.runOnce();

		view(token, "IGNORED").andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].files[0].status").value("IGNORED"));
		view(token, "TO_REVIEW").andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void failedFileCanBeRetried() throws Exception {
		String token = oneInvoice("imp11@gmail.com", "imp11");
		String fileId = firstFileId(token, "TO_REVIEW");
		act(token, "import", List.of(fileId)).andExpect(status().isOk());
		google.removeEmail("imp11@gmail.com", "m1");
		importWorker.runOnce();
		view(token, "TO_REVIEW").andExpect(jsonPath("$[0].files[0].status").value("FAILED"));

		act(token, "import", List.of(fileId)).andExpect(jsonPath("$[0].status").value("IMPORTING"))
				.andExpect(jsonPath("$[0].error").doesNotExist());
	}

	@Test
	void deletedDocumentCanBeImportedAgain() throws Exception {
		String token = oneInvoice("imp12@gmail.com", "imp12");
		String fileId = firstFileId(token, "TO_REVIEW");
		act(token, "import", List.of(fileId)).andExpect(status().isOk());
		importWorker.runOnce();
		String documentId = JsonPath.read(viewBody(token, "IMPORTED"), "$[0].files[0].document.id");
		mvc.perform(delete("/api/documents/" + documentId).header("Authorization", bearer(token)))
				.andExpect(status().isNoContent());

		view(token, "IMPORTED").andExpect(jsonPath("$[0].files[0].status").value("IMPORTED"))
				.andExpect(jsonPath("$[0].files[0].document").doesNotExist());
		act(token, "import", List.of(fileId)).andExpect(jsonPath("$[0].status").value("IMPORTING"));
		// Once imported again, its document exists, so another import click changes nothing:
		importWorker.runOnce();
		act(token, "import", List.of(fileId)).andExpect(jsonPath("$[0].status").value("IMPORTED"));
	}

	@Test
	void disconnectKeepsImportedDocuments() throws Exception {
		String token = oneInvoice("imp13@gmail.com", "imp13");
		String accountId = JsonPath.<List<String>>read(
				mvc.perform(get(GMAIL).header("Authorization", bearer(token))).andReturn().getResponse()
						.getContentAsString(), "$.accounts[*].id").get(0);
		act(token, "import", List.of(firstFileId(token, "TO_REVIEW"))).andExpect(status().isOk());
		importWorker.runOnce();

		mvc.perform(delete(GMAIL + "/accounts/" + accountId).header("Authorization", bearer(token)))
				.andExpect(status().isNoContent());

		assertThat(documentCount(token)).isEqualTo(1);
		view(token, "IMPORTED").andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void otherUsersFileIs404AndNothingChanges() throws Exception {
		String tokenA = oneInvoice("imp14@gmail.com", "imp14a");
		String tokenB = registerAndGetToken(uniqueEmail("imp14b"));
		String fileId = firstFileId(tokenA, "TO_REVIEW");

		act(tokenB, "import", List.of(fileId)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GMAIL_FILE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("That file was not found."));
		// One of the user's own files plus an unknown one: still nothing changes.
		act(tokenA, "ignore", List.of(fileId, UUID.randomUUID().toString())).andExpect(status().isNotFound());

		view(tokenA, "TO_REVIEW").andExpect(jsonPath("$[0].files[0].status").value("NEW"));
	}

	@Test
	void emptyOrTooManyIdsIs400() throws Exception {
		String token = registerAndGetToken(uniqueEmail("imp15"));
		act(token, "import", List.of()).andExpect(status().isBadRequest());
		List<String> many = new ArrayList<>();
		for (int i = 0; i < 101; i++) {
			many.add(UUID.randomUUID().toString());
		}
		act(token, "import", many).andExpect(status().isBadRequest());
	}
}
