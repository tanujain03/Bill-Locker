package project.bill_locker.service;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.ApiTest;

/** The repair and maintenance history of a product, and its next service date. */
class ServiceRecordApiTests extends ApiTest {

	private static final LocalDate TODAY = LocalDate.now();

	@Test
	void logEditAndDeleteTheServicesOfAProduct() throws Exception {
		String token = registerAndGetToken(uniqueEmail("services"));
		String acId = createProduct(token, """
				{"name": "Split AC", "brand": "Daikin"}
				""");
		save(token, null, """
				{"productId": "%s", "serviceDate": "%s", "serviceType": "INSTALLATION", "nextServiceDate": "%s"}
				""".formatted(acId, TODAY.minusMonths(6), TODAY.minusMonths(3)))
				.andExpect(status().isCreated());
		String latest = save(token, null, """
				{"productId": "%s", "serviceDate": "%s", "serviceType": "ROUTINE_MAINTENANCE", "serviceCenter": " Cool Care ",
				 "cost": 1499, "nextServiceDate": "%s", "notes": "Gas top-up"}
				""".formatted(acId, TODAY.minusDays(10), TODAY.plusMonths(6)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.productName").value("Split AC"))
				.andExpect(jsonPath("$.serviceCenter").value("Cool Care"))
				.andExpect(jsonPath("$.cost").value(1499.0))
				.andExpect(jsonPath("$.currency").value("INR"))
				.andReturn().getResponse().getContentAsString();
		String latestId = JsonPath.read(latest, "$.id");

		mvc.perform(get("/api/service-records").param("productId", acId).header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].serviceType", contains("ROUTINE_MAINTENANCE", "INSTALLATION"))); // newest first
		// The product's next service comes from its latest record, not the older one.
		mvc.perform(get("/api/products/" + acId).header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.nextServiceDate").value(TODAY.plusMonths(6).toString()));
		mvc.perform(get("/api/products").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[0].nextServiceDate").value(TODAY.plusMonths(6).toString()));

		save(token, latestId, """
				{"productId": "%s", "serviceDate": "%s", "serviceType": "REPAIR"}
				""".formatted(acId, TODAY.minusDays(10)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.serviceType").value("REPAIR"))
				.andExpect(jsonPath("$.nextServiceDate").value(nullValue()));
		mvc.perform(get("/api/products/" + acId).header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.nextServiceDate").value(nullValue()));

		mvc.perform(delete("/api/service-records/" + latestId).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/service-records").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void rejectsMissingDetailsAndImpossibleDates() throws Exception {
		String token = registerAndGetToken(uniqueEmail("service-dates"));
		String productId = createProduct(token, """
				{"name": "Washing Machine"}
				""");

		save(token, null, """
				{"serviceDate": "%s"}
				""".formatted(TODAY.plusDays(1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.productId").value("Choose a product"))
				.andExpect(jsonPath("$.fieldErrors.serviceType").value("Choose a service type"))
				.andExpect(jsonPath("$.fieldErrors.serviceDate").value("Service date cannot be in the future"));
		save(token, null, """
				{"productId": "%s", "serviceDate": "%s", "serviceType": "REPAIR", "nextServiceDate": "%s"}
				""".formatted(productId, TODAY, TODAY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.nextServiceDate").value("The next service must be after the service date"));
	}

	@Test
	void anotherUsersRecordsAndProductsLookMissing() throws Exception {
		String owner = registerAndGetToken(uniqueEmail("service-owner"));
		String productId = createProduct(owner, """
				{"name": "Water Purifier"}
				""");
		String record = save(owner, null, """
				{"productId": "%s", "serviceDate": "%s", "serviceType": "INSPECTION"}
				""".formatted(productId, TODAY))
				.andReturn().getResponse().getContentAsString();
		String recordId = JsonPath.read(record, "$.id");
		String stranger = registerAndGetToken(uniqueEmail("service-stranger"));

		save(stranger, recordId, """
				{"productId": "%s", "serviceDate": "%s", "serviceType": "REPAIR"}
				""".formatted(productId, TODAY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SERVICE_RECORD_NOT_FOUND"));
		mvc.perform(delete("/api/service-records/" + recordId).header(AUTHORIZATION, bearer(stranger)))
				.andExpect(status().isNotFound());
		save(stranger, null, """
				{"productId": "%s", "serviceDate": "%s", "serviceType": "REPAIR"}
				""".formatted(productId, TODAY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
		mvc.perform(get("/api/service-records").header(AUTHORIZATION, bearer(stranger)))
				.andExpect(jsonPath("$", empty()));
	}

	@Test
	void deletingAProductDeletesItsServiceHistory() throws Exception {
		String token = registerAndGetToken(uniqueEmail("service-cascade"));
		String productId = createProduct(token, """
				{"name": "Car"}
				""");
		save(token, null, """
				{"productId": "%s", "serviceDate": "%s", "serviceType": "ROUTINE_MAINTENANCE"}
				""".formatted(productId, TODAY))
				.andExpect(status().isCreated());

		mvc.perform(delete("/api/products/" + productId).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/service-records").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$", empty()));
	}

	/** POST a new record ({@code recordId} null) or PUT changes to an existing one. */
	private ResultActions save(String token, String recordId, String json) throws Exception {
		return mvc.perform((recordId == null ? post("/api/service-records") : put("/api/service-records/" + recordId))
				.header(AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}
}
