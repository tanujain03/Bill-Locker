package project.bill_locker.product;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.document.DocumentApiTestBase;

class StableProductApiTests extends DocumentApiTestBase {

	private String details(String token, String id) throws Exception {
		return mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private ResultActions save(String token, String id, String items) throws Exception {
		return mvc.perform(put("/api/documents/" + id).header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"documentType\":\"INVOICE\",\"items\":" + items + "}"));
	}

	private ResultActions product(String token, String id) throws Exception {
		return mvc.perform(get("/api/products/" + id).header("Authorization", bearer(token)));
	}

	@Test
	void linksSurviveEditingReorderingAndRemovalOfAnotherProduct() throws Exception {
		String token = registerAndGetToken(uniqueEmail("stable"));
		String doc = saveBill(token, """
				{"documentType":"INVOICE","items":[{"productName":"Phone"},{"productName":"Charger","warrantyPeriodMonths":12}]}""");
		String original = details(token, doc);
		String phone = JsonPath.read(original, "$.items[0].id");
		String charger = JsonPath.read(original, "$.items[1].id");
		save(token, doc, """
				[{"id":"%s","productName":"Updated charger","warrantyPeriodMonths":12},
				 {"id":"%s","productName":"Updated phone"}]
				""".formatted(charger, phone))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(charger));
		product(token, phone).andExpect(status().isOk())
				.andExpect(jsonPath("$.productName").value("Updated phone"))
				.andExpect(jsonPath("$.position").value(1));
		mvc.perform(get("/api/products/" + charger + "/claim-kit").header("Authorization", bearer(token)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.productName").value("Updated charger"));
		mvc.perform(put("/api/products/" + charger + "/registration").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"registered\":true}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.productId").value(charger));
		product(token, phone).andExpect(jsonPath("$.warrantyRegistered").value(false));
		save(token, doc, """
				[{"id":"%s","productName":"Updated charger","warrantyPeriodMonths":12,"warrantyRegistered":true}]
				""".formatted(charger)).andExpect(status().isOk());
		product(token, charger).andExpect(status().isOk()).andExpect(jsonPath("$.position").value(0))
				.andExpect(jsonPath("$.warrantyRegistered").value(true));
		product(token, phone).andExpect(status().isNotFound());
		mvc.perform(get("/api/warranties").header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.items[0].productId").value(charger));
	}

	@Test
	void newProductsGetIdsImmediatelyAndNeverReuseDeletedProductLinks() throws Exception {
		String token = registerAndGetToken(uniqueEmail("stable-new"));
		String doc = uploadPdf(token, "bill.pdf");
		String first = save(token, doc, "[{\"productName\":\"Phone\"}]").andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").isNotEmpty()).andReturn().getResponse().getContentAsString();
		String oldId = JsonPath.read(first, "$.items[0].id");
		save(token, doc, "[]").andExpect(status().isOk());
		String next = save(token, doc, "[{\"productName\":\"Phone\"}]").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertNotEquals(oldId, JsonPath.read(next, "$.items[0].id"));
		product(token, oldId).andExpect(status().isNotFound());
		mvc.perform(get("/api/products/" + oldId + "/claim-kit").header("Authorization", bearer(token)))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsForeignAndDuplicateIdsWithoutChangingTheBill() throws Exception {
		String token = registerAndGetToken(uniqueEmail("stable-owner"));
		String other = registerAndGetToken(uniqueEmail("stable-other"));
		String doc = saveBill(token, "{\"items\":[{\"productName\":\"Phone\"}]}");
		String foreignDoc = saveBill(other, "{\"items\":[{\"productName\":\"Private phone\"}]}");
		String ownId = JsonPath.read(details(token, doc), "$.items[0].id");
		String foreignId = JsonPath.read(details(other, foreignDoc), "$.items[0].id");
		save(token, doc, """
				[{"id":"%s","productName":"Changed"},{"id":"%s","productName":"Foreign"}]
				""".formatted(ownId, foreignId)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ITEM_ID"));
		save(token, doc, """
				[{"id":"%s","productName":"Changed"},{"id":"%s","productName":"Duplicate"}]
				""".formatted(ownId, ownId)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DUPLICATE_ITEM_ID"));
		product(token, ownId).andExpect(jsonPath("$.productName").value("Phone"));
		product(token, foreignId).andExpect(status().isNotFound());
		mvc.perform(get("/api/products/" + foreignId + "/claim-kit").header("Authorization", bearer(token)))
				.andExpect(status().isNotFound());
		mvc.perform(put("/api/products/" + foreignId + "/registration").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"registered\":true}")).andExpect(status().isNotFound());
		product(token, UUID.randomUUID().toString()).andExpect(status().isNotFound());
		mvc.perform(get("/api/products/" + ownId)).andExpect(status().isUnauthorized());
	}

	@Test
	void rereadingAnUnambiguousProductPreservesItsIdAndRegistration() throws Exception {
		String token = registerAndGetToken(uniqueEmail("stable-read"));
		String doc = saveBill(token, """
				{"items":[{"productName":"Phone","serialNumber":"SN-1","warrantyRegistered":true}]}""");
		String id = JsonPath.read(details(token, doc), "$.items[0].id");
		// The fake AI returns the same SN-1 phone plus a charger, with no server IDs.
		mvc.perform(post("/api/documents/" + doc + "/extract").header("Authorization", bearer(token)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id))
				.andExpect(jsonPath("$.items[0].warrantyRegistered").value(true))
				.andExpect(jsonPath("$.items[1].id").isNotEmpty());
		product(token, id).andExpect(status().isOk());
	}

	@Test
	void unidentifiedOrAmbiguousProductsCannotTakeOverAnExistingLink() throws Exception {
		String token = registerAndGetToken(uniqueEmail("stable-ambiguous"));
		String doc = saveBill(token, "{\"items\":[{\"productName\":\"Phone\",\"serialNumber\":\"SN-1\"}]}");
		String oldId = JsonPath.read(details(token, doc), "$.items[0].id");
		save(token, doc, """
				[{"productName":"Phone","serialNumber":"SN-1"},{"productName":"Phone","serialNumber":"SN-1"}]
				""").andExpect(status().isOk());
		product(token, oldId).andExpect(status().isNotFound());
	}

	@Test
	void registrationPageSearchUsesThePermanentIdAfterRemovalOfAnotherItem() throws Exception {
		String token = registerAndGetToken(uniqueEmail("stable-search"));
		String doc = saveBill(token, """
				{"items":[{"productName":"Phone"},{"productName":"Noise Buds","brand":"Noise"}]}""");
		String id = JsonPath.read(details(token, doc), "$.items[1].id");
		save(token, doc, """
				[{"id":"%s","productName":"Noise Buds","brand":"Noise"}]
				""".formatted(id)).andExpect(status().isOk());
		mvc.perform(post("/api/documents/" + doc + "/registration-page").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":\"" + id + "\",\"brand\":\"Noise\"}"))
				.andExpect(status().isOk());
		product(token, id).andExpect(jsonPath("$.registrationUrl").isNotEmpty());
		mvc.perform(post("/api/documents/" + doc + "/registration-page").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":\"" + UUID.randomUUID() + "\",\"brand\":\"Noise\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void olderRequestsPreserveAnUnambiguousModelAndNameButNeverMatchByNameAlone() throws Exception {
		String token = registerAndGetToken(uniqueEmail("stable-model"));
		String doc = saveBill(token, "{\"items\":[{\"productName\":\"Phone\",\"modelNumber\":\"M-1\"}]}");
		String id = JsonPath.read(details(token, doc), "$.items[0].id");
		save(token, doc, "[{\"productName\":\" phone \",\"modelNumber\":\"m-1\"}]").andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(id));
		save(token, doc, "[{\"productName\":\"Phone\"}]").andExpect(status().isOk());
		product(token, id).andExpect(status().isNotFound());
	}

	@Test
	void deletingBillRemovesStableProductAndClaimLinks() throws Exception {
		String token = registerAndGetToken(uniqueEmail("stable-delete"));
		String doc = saveBill(token, "{\"items\":[{\"productName\":\"Phone\"}]}");
		String id = JsonPath.read(details(token, doc), "$.items[0].id");
		mvc.perform(delete("/api/documents/" + doc).header("Authorization", bearer(token))).andExpect(status().isNoContent());
		product(token, id).andExpect(status().isNotFound());
		mvc.perform(get("/api/products/" + id + "/claim-kit").header("Authorization", bearer(token)))
				.andExpect(status().isNotFound());
	}
}
