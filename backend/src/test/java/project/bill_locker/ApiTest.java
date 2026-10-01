package project.bill_locker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
