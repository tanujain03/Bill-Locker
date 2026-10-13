package project.bill_locker.sample;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.document.DocumentApiTestBase;

/** /api/sample-data — "today" is 2026-10-07 (FixedClockConfig). */
class SampleDataApiTests extends DocumentApiTestBase {

	private ResultActions call(String token, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		return mvc.perform(request.header("Authorization", bearer(token)));
	}

	@Test
	void addingFillsEveryPage() throws Exception {
		String token = registerAndGetToken(uniqueEmail("sample-add"));
		call(token, get("/api/sample-data")).andExpect(jsonPath("$.sampleBills").value(0));

		String body = call(token, post("/api/sample-data"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sampleBills").value(greaterThanOrEqualTo(25)))
				.andReturn().getResponse().getContentAsString();
		int count = JsonPath.read(body, "$.sampleBills");

		// Saved, flagged as sample, shown on the dashboard.
		call(token, get("/api/documents")).andExpect(jsonPath("$.length()").value(count))
				.andExpect(jsonPath("$[*].sample", everyItem(is(true))))
				.andExpect(jsonPath("$[*].status", everyItem(is("SAVED"))));
		call(token, get("/api/dashboard")).andExpect(jsonPath("$.savedBills").value(count));

		// Every warranty state appears (the end dates were worked out from start + months).
		call(token, get("/api/warranties"))
				.andExpect(jsonPath("$.counts.expiringSoon").value(1))
				.andExpect(jsonPath("$.counts.expired").value(1))
				.andExpect(jsonPath("$.counts.active").value(4))
				.andExpect(jsonPath("$.counts.noInfo").value(1));

		// Monthly bills become recurring payments on the Expenses page.
		call(token, get("/api/expenses"))
				.andExpect(jsonPath("$.recurring[*].name", hasItems("Airtel Postpaid", "Netflix", "BESCOM Electricity")));
	}

	@Test
	void sampleBillsHaveARealPdf() throws Exception {
		String token = registerAndGetToken(uniqueEmail("sample-pdf"));
		call(token, post("/api/sample-data")).andExpect(status().isOk());
		String list = call(token, get("/api/documents")).andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(list, "$[*].id");

		byte[] pdf = call(token, get("/api/documents/" + ids.getFirst() + "/download"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andReturn().getResponse().getContentAsByteArray();
		org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
	}

	@Test
	void addingTwiceIsRefused() throws Exception {
		String token = registerAndGetToken(uniqueEmail("sample-twice"));
		call(token, post("/api/sample-data")).andExpect(status().isOk());

		call(token, post("/api/sample-data"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SAMPLE_DATA_EXISTS"));
	}

	@Test
	void removingKeepsTheUsersOwnBillsAndOtherUsersSamples() throws Exception {
		String token = registerAndGetToken(uniqueEmail("sample-remove"));
		String other = registerAndGetToken(uniqueEmail("sample-other"));
		String mine = saveBill(token, """
				{"sellerName": "My own shop", "purchaseDate": "2026-10-01", "totalAmount": 100}""");
		call(token, post("/api/sample-data")).andExpect(status().isOk());
		String otherBody = call(other, post("/api/sample-data")).andReturn().getResponse().getContentAsString();
		int otherCount = JsonPath.read(otherBody, "$.sampleBills");

		call(token, delete("/api/sample-data")).andExpect(status().isOk()).andExpect(jsonPath("$.sampleBills").value(0));

		call(token, get("/api/documents"))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(mine))
				.andExpect(jsonPath("$[0].sample").value(false));
		call(other, get("/api/sample-data")).andExpect(jsonPath("$.sampleBills").value(otherCount));
		// And it can be added again after removing.
		call(token, post("/api/sample-data")).andExpect(status().isOk());
	}

	@Test
	void needsSignIn() throws Exception {
		mvc.perform(post("/api/sample-data")).andExpect(status().isUnauthorized());
	}
}
