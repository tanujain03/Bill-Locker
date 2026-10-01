package project.bill_locker.product;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import project.bill_locker.ApiTest;

/** Categories, and adding, finding, editing and deleting products with their warranty. */
class ProductApiTests extends ApiTest {

	private static final byte[] PDF = "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

	@Test
	void listsTheStandardCategoriesInOrder() throws Exception {
		String token = registerAndGetToken(uniqueEmail("categories"));

		mvc.perform(get("/api/categories").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(9)))
				.andExpect(jsonPath("$[0].slug").value("mobile-phones"))
				.andExpect(jsonPath("$[0].name").value("Mobile Phones"))
				.andExpect(jsonPath("$[8].slug").value("other"));
	}

	@Test
	void addViewEditAndDeleteAProduct() throws Exception {
		String token = registerAndGetToken(uniqueEmail("products"));
		String kitchen = categoryId(token, "kitchen");
		LocalDate bought = LocalDate.now().minusMonths(3);

		String id = createProduct(token, """
				{"name": "  Philips Air Fryer  ", "categoryId": "%s", "brand": "Philips", "model": "",
				 "purchaseDate": "%s", "purchasePrice": 8999, "warrantyMonths": 24}
				""".formatted(kitchen, bought));

		mvc.perform(get("/api/products/" + id).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Philips Air Fryer"))
				.andExpect(jsonPath("$.model").value(nullValue()))
				.andExpect(jsonPath("$.categorySlug").value("kitchen"))
				.andExpect(jsonPath("$.purchasePrice").value(8999.0))
				.andExpect(jsonPath("$.currency").value("INR"))
				.andExpect(jsonPath("$.warranty.warrantyMonths").value(24))
				.andExpect(jsonPath("$.warranty.startDate").value(bought.toString()))
				.andExpect(jsonPath("$.warranty.expiryDate").value(bought.plusMonths(24).minusDays(1).toString()))
				.andExpect(jsonPath("$.warranty.status").value("ACTIVE"))
				.andExpect(jsonPath("$.nextServiceDate").value(nullValue()))
				.andExpect(jsonPath("$.documentCount").value(0));

		// PUT replaces everything: no warranty period any more means an unknown warranty.
		mvc.perform(put("/api/products/" + id)
						.header(AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Philips Air Fryer XL", "purchaseDate": "%s"}
								""".formatted(bought)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Philips Air Fryer XL"))
				.andExpect(jsonPath("$.categoryId").value(nullValue()))
				.andExpect(jsonPath("$.brand").value(nullValue()))
				.andExpect(jsonPath("$.warranty.warrantyMonths").value(nullValue()))
				.andExpect(jsonPath("$.warranty.expiryDate").value(nullValue()))
				.andExpect(jsonPath("$.warranty.status").value("UNKNOWN"));

		mvc.perform(delete("/api/products/" + id).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/products/" + id).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void searchAndFilterProducts() throws Exception {
		String token = registerAndGetToken(uniqueEmail("search"));
		LocalDate today = LocalDate.now();
		createProduct(token, """
				{"name": "Inspiron 15 Laptop", "brand": "Dell", "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(today.minusMonths(12).plusDays(10))); // expires in about 10 days
		createProduct(token, """
				{"name": "Double Door Refrigerator", "brand": "LG", "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(today.minusYears(3)));
		createProduct(token, """
				{"name": "WH-1000XM5 Headphones", "brand": "Sony", "categoryId": "%s", "seller": "Croma"}
				""".formatted(categoryId(token, "audio")));

		mvc.perform(get("/api/products").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].name", contains("WH-1000XM5 Headphones", "Double Door Refrigerator",
						"Inspiron 15 Laptop"))); // newest first
		mvc.perform(get("/api/products").param("search", "dell").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].name", contains("Inspiron 15 Laptop")));
		mvc.perform(get("/api/products").param("search", "CROMA").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].name", contains("WH-1000XM5 Headphones")));
		mvc.perform(get("/api/products").param("categoryId", categoryId(token, "audio")).header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].name", contains("WH-1000XM5 Headphones")));
		mvc.perform(get("/api/products").param("warrantyStatus", "EXPIRING_SOON").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].name", contains("Inspiron 15 Laptop")));
		mvc.perform(get("/api/products").param("warrantyStatus", "EXPIRED").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].name", contains("Double Door Refrigerator")));
		mvc.perform(get("/api/products").param("warrantyStatus", "UNKNOWN").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].name", contains("WH-1000XM5 Headphones")));

		mvc.perform(get("/api/warranties").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].productName", contains("Double Door Refrigerator", "Inspiron 15 Laptop",
						"WH-1000XM5 Headphones"))) // soonest expiry first, unknown last
				.andExpect(jsonPath("$[0].status").value("EXPIRED"))
				.andExpect(jsonPath("$[0].productBrand").value("LG"))
				.andExpect(jsonPath("$[2].categorySlug").value("audio"));
		mvc.perform(get("/api/warranties").param("status", "EXPIRING_SOON").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$[*].productName", contains("Inspiron 15 Laptop")));
	}

	@Test
	void rejectsInvalidDetailsFieldByField() throws Exception {
		String token = registerAndGetToken(uniqueEmail("invalid"));

		mvc.perform(post("/api/products")
						.header(AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": " ", "purchaseDate": "%s", "purchasePrice": -5, "currency": "rupees",
								 "warrantyMonths": 300}
								""".formatted(LocalDate.now().plusDays(1))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.name").value("Product name is required"))
				.andExpect(jsonPath("$.fieldErrors.purchaseDate").value("Purchase date cannot be in the future"))
				.andExpect(jsonPath("$.fieldErrors.purchasePrice").value("Enter a valid amount"))
				.andExpect(jsonPath("$.fieldErrors.currency").value("Use a 3-letter currency code, e.g. INR"))
				.andExpect(jsonPath("$.fieldErrors.warrantyMonths").value("Enter a whole number of months (0–240)"));

		mvc.perform(post("/api/products")
						.header(AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Phone", "categoryId": "%s"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.categoryId").value("Unknown category"));
	}

	@Test
	void anotherUsersProductLooksMissing() throws Exception {
		String owner = registerAndGetToken(uniqueEmail("owner"));
		String id = createProduct(owner, """
				{"name": "Galaxy S24"}
				""");
		String stranger = registerAndGetToken(uniqueEmail("stranger"));

		mvc.perform(get("/api/products/" + id).header(AUTHORIZATION, bearer(stranger)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
		mvc.perform(put("/api/products/" + id)
						.header(AUTHORIZATION, bearer(stranger))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Mine now"}
								"""))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/products/" + id).header(AUTHORIZATION, bearer(stranger)))
				.andExpect(status().isNotFound());
		mvc.perform(multipart("/api/documents/upload")
						.file(new MockMultipartFile("file", "bill.pdf", "application/pdf", PDF))
						.param("productId", id)
						.header(AUTHORIZATION, bearer(stranger)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
		mvc.perform(get("/api/products").header(AUTHORIZATION, bearer(stranger)))
				.andExpect(jsonPath("$", empty()));

		mvc.perform(get("/api/products/" + id).header(AUTHORIZATION, bearer(owner)))
				.andExpect(jsonPath("$.name").value("Galaxy S24"));
	}

	@Test
	void deletingAProductKeepsItsDocuments() throws Exception {
		String token = registerAndGetToken(uniqueEmail("keep-bills"));
		String productId = createProduct(token, """
				{"name": "Bosch Washing Machine"}
				""");
		String documentId = uploadFile(token, "bill.pdf", "application/pdf", PDF, productId);

		mvc.perform(get("/api/documents").param("productId", productId).header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(documentId))
				.andExpect(jsonPath("$[0].productName").value("Bosch Washing Machine"));
		mvc.perform(get("/api/products/" + productId).header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.documentCount").value(1));

		mvc.perform(delete("/api/products/" + productId).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/documents/" + documentId).header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productId").value(nullValue()))
				.andExpect(jsonPath("$.productName").value(nullValue()));
	}
}
