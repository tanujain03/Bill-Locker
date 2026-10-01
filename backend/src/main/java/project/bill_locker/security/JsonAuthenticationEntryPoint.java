package project.bill_locker.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers 401 in the API's error format when a request has no valid token
 * (missing, expired or changed). The frontend then sends the user to the login page.
 */
@Component
class  JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final String BODY =
			"{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"Please sign in to continue.\"}";

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
			throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setHeader("WWW-Authenticate", "Bearer");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(BODY);
	}
}
