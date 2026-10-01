package project.bill_locker;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Base class for API tests. {@link MockMvc} sends requests through the real
 * security filters and controllers (without opening a network port), and the
 * data goes to a throwaway PostgreSQL started by {@link TestcontainersConfiguration}.
 * The background document reader is switched off, so tests that need it run it
 * themselves at a known moment.
 */
@SpringBootTest(properties = "app.processing.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTest {

	protected static final String PASSWORD = "Str0ngPass";

	@Autowired
	protected MockMvc mvc;

	/** Registers a new account and returns its login token. */
	protected String registerAndGetToken(String email) throws Exception {
		String response = mvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerJson("Test User", email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.token");
	}

	/** Adds a product through the API and returns its id. {@code json} is a ProductInput. */
	protected String createProduct(String token, String json) throws Exception {
		String response = mvc.perform(post("/api/products")
						.header(AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	/** The id of the category with this slug, e.g. "kitchen". */
	protected String categoryId(String token, String slug) throws Exception {
		String response = mvc.perform(get("/api/categories").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(response, "$[?(@.slug == '" + slug + "')].id");
		return ids.getFirst();
	}

	/** Uploads a file, for a product when {@code productId} isn't null, and returns the document's id. */
	protected String uploadFile(String token, String fileName, String contentType, byte[] content, String productId)
			throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/documents/upload")
				.file(new MockMultipartFile("file", fileName, contentType, content));
		if (productId != null) {
			request.param("productId", productId);
		}
		String response = mvc.perform(request.header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	protected static String registerJson(String name, String email, String password) {
		return """
				{"name": "%s", "email": "%s", "password": "%s"}
				""".formatted(name, email, password);
	}

	protected static String loginJson(String email, String password) {
		return """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
	}

	protected static String bearer(String token) {
		return "Bearer " + token;
	}

	/** Every test uses new accounts, so tests never see each other's data. */
	protected static String uniqueEmail(String name) {
		return name + "-" + UUID.randomUUID() + "@example.com";
	}
}
