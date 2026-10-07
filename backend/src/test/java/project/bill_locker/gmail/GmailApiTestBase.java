package project.bill_locker.gmail;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import project.bill_locker.ApiTest;
import project.bill_locker.FakeGoogleApi;

/** Helpers shared by the Gmail API tests. */
abstract class GmailApiTestBase extends ApiTest {

	@Autowired
	protected FakeGoogleApi google;

	@AfterEach
	void resetGoogle() {
		google.reset();
	}

	/** The browser-binding cookie from the last startConnect. */
	protected Cookie lastCookie;

	/** Starts "Connect" and returns the state Google would send back. */
	protected String startConnect(String token) throws Exception {
		MockHttpServletResponse raw = mvc.perform(post("/api/integrations/gmail/connect").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andReturn().getResponse();
		lastCookie = raw.getCookie("gmail_connect");
		String response = raw.getContentAsString();
		String url = JsonPath.read(response, "$.authorizationUrl");
		String afterState = url.substring(url.indexOf("state=") + "state=".length());
		return afterState.substring(0, afterState.indexOf('&'));
	}

	/** Google's redirect back to us. No Authorization header: it is the browser coming from Google. */
	protected String callback(String query) throws Exception {
		return callback(query, lastCookie);
	}

	protected String callback(String query, Cookie cookie) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/integrations/gmail/callback?" + query);
		if (cookie != null) {
			request.cookie(cookie);
		}
		return mvc.perform(request)
				.andExpect(status().isFound())
				.andReturn().getResponse().getHeader("Location");
	}

	/** Connects the address for this user and returns the new account's id. */
	protected String connect(String token, String address) throws Exception {
		String state = startConnect(token);
		google.willAuthorize("code-" + address, address);
		callback("code=code-" + address + "&state=" + state);
		String response = mvc.perform(get("/api/integrations/gmail").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(response, "$.accounts[?(@.email == '" + address + "')].id");
		return ids.get(0);
	}
}
