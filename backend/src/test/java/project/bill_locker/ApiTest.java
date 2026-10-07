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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base class for API tests. {@link MockMvc} sends requests through the real
 * security filters and controllers (without opening a network port), and the
 * data goes to a throwaway PostgreSQL started by {@link TestcontainersConfiguration}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingResetLinkSender.Config.class})
public abstract class ApiTest {

	protected static final String PASSWORD = "Str0ngPass";

	@Autowired
	protected MockMvc mvc;

	/** POSTs a JSON body to an open (no token) endpoint. */
	protected ResultActions postJson(String url, String json) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/** Registers a new account and returns its login token. */
	protected String registerAndGetToken(String email) throws Exception {
		String response = postJson("/api/auth/register", registerJson("Test User", email, PASSWORD))
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
