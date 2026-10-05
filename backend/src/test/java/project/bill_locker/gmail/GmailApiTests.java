package project.bill_locker.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;
import project.bill_locker.ApiTest;
import project.bill_locker.FakeGoogleApi;

/**
 * Connecting Gmail, scanning for bills, importing and disconnecting, against the fake
 * Google in {@link FakeGoogleApi} (its inbox: an invoice, a warranty card, a newsletter
 * and an email without a file).
 */
class GmailApiTests extends ApiTest {

	@Autowired
	private GmailScanWorker scanner;

	@Autowired
	private FakeGoogleApi.Fake google;

	@Test
	void connectScanImportAndDisconnect() throws Exception {
		String token = registerAndGetToken(uniqueEmail("gmail"));
		connect(token);

		mvc.perform(get("/api/integrations/gmail").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.connected").value(true))
				.andExpect(jsonPath("$.email").value("asha@gmail.com"))
				.andExpect(jsonPath("$.syncStatus").value("SYNCING")); // the first scan starts by itself

		scanner.scanPending();

		mvc.perform(get("/api/integrations/gmail").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.syncStatus").value("IDLE"))
				.andExpect(jsonPath("$.lastSyncedAt").isString());
		String shortlist = mvc.perform(get("/api/integrations/gmail/messages").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].subject", contains("Your Croma tax invoice", "Warranty registration confirmed",
						"Big sale ends tonight"))) // newest first; the email without a file is left out
				.andExpect(jsonPath("$[0].fromName").value("Croma"))
				.andExpect(jsonPath("$[0].fromEmail").value("orders@croma.com"))
				.andExpect(jsonPath("$[0].detectedType").value("INVOICE"))
				.andExpect(jsonPath("$[0].confidence").value(0.95))
				.andExpect(jsonPath("$[0].attachments[0].fileName").value("Invoice_408-123.pdf"))
				.andExpect(jsonPath("$[0].attachments[0].attachmentId").doesNotExist()) // Gmail's ids stay on the server
				.andExpect(jsonPath("$[1].detectedType").value("WARRANTY_CARD"))
				.andExpect(jsonPath("$[2].confidence").value(0.15))
				.andReturn().getResponse().getContentAsString();
		mvc.perform(get("/api/notifications").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[0].type").value("GMAIL_BILLS_FOUND"))
				.andExpect(jsonPath("$[0].message").value("Found 2 bills in your inbox. Review and import them."));

		List<String> ids = JsonPath.read(shortlist, "$[*].id");
		mvc.perform(post("/api/integrations/gmail/import")
						.header(AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"messageIds\": [\"%s\", \"%s\"]}".formatted(ids.get(0), ids.get(1))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.documents", hasSize(2)))
				.andExpect(jsonPath("$.documents[*].source", everyItem(is("GMAIL"))))
				.andExpect(jsonPath("$.documents[*].processingStatus", everyItem(is("UPLOADED"))))
				.andExpect(jsonPath("$.documents[*].documentType", containsInAnyOrder("INVOICE", "WARRANTY_CARD")));
		mvc.perform(post("/api/integrations/gmail/messages/" + ids.get(2) + "/ignore").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.status").value("IGNORED"));
		mvc.perform(get("/api/integrations/gmail/messages").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[0].status").value("IMPORTED"))
				.andExpect(jsonPath("$[0].documentIds", hasSize(1)));

		mvc.perform(delete("/api/integrations/gmail").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isNoContent());
		assertThat(google.revoked).as("access withdrawn at Google").contains(FakeGoogleApi.Fake.REFRESH_TOKEN);
		mvc.perform(get("/api/integrations/gmail").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.connected").value(false));
		mvc.perform(get("/api/integrations/gmail/messages").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$", empty()));
		mvc.perform(get("/api/documents").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$", hasSize(2))); // imported bills stay in the locker
	}

	@Test
	void aSignInThatWasNotGivenOrWasForgedGoesBackWithAnError() throws Exception {
		String token = registerAndGetToken(uniqueEmail("gmail-denied"));
		String state = startConnect(token);

		mvc.perform(get("/api/integrations/gmail/callback").param("error", "access_denied").param("state", state))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", startsWith("http://localhost:5173/gmail?status=error&reason=")));
		// A state works once: using it again (or a made-up one) fails too.
		mvc.perform(get("/api/integrations/gmail/callback").param("code", FakeGoogleApi.Fake.GOOD_CODE).param("state", state))
				.andExpect(header().string("Location", startsWith("http://localhost:5173/gmail?status=error")));
		mvc.perform(get("/api/integrations/gmail").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.connected").value(false));
	}

	@Test
	void withoutAConnectionThereIsNothingToScanOrImport() throws Exception {
		String token = registerAndGetToken(uniqueEmail("gmail-none"));

		mvc.perform(post("/api/integrations/gmail/sync").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GMAIL_NOT_CONNECTED"));
		mvc.perform(post("/api/integrations/gmail/import")
						.header(AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"messageIds\": []}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.messageIds").value("Choose at least one email"));
	}

	/** Connect → (Google's consent page) → callback, as the browser would. */
	private void connect(String token) throws Exception {
		String state = startConnect(token);
		mvc.perform(get("/api/integrations/gmail/callback").param("code", FakeGoogleApi.Fake.GOOD_CODE).param("state", state))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "http://localhost:5173/gmail?status=connected"));
	}

	/** Returns the state from the Google address the app would send the browser to. */
	private String startConnect(String token) throws Exception {
		String response = mvc.perform(post("/api/integrations/gmail/connect").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authorizationUrl", startsWith("https://accounts.google.com/")))
				.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(response, "$.authorizationUrl");
		return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
	}
}
