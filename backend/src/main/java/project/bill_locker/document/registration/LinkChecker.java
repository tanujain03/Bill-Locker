package project.bill_locker.document.registration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Checks that a link the AI found really opens, before we show it: an AI can make up a
 * plausible address ("noise.com/warranty-register") that doesn't exist.
 *
 * <p>Because the address comes from outside, it may only point to the public internet:
 * https only, and never to this machine or a private network (otherwise a crafted
 * link could make our server call its own internal services). Redirects are followed
 * by hand so each hop is checked the same way.
 */
public class LinkChecker {

	private static final int MAX_REDIRECTS = 4;
	/** "It exists, it just doesn't like robots": many brand sites answer bots with these. */
	private static final Set<Integer> EXISTS_BUT_BLOCKED = Set.of(401, 403, 405, 429);

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	/** The page's final address when it opens, empty when it doesn't (or isn't allowed). */
	public Optional<String> opens(String link) {
		try {
			URI uri = URI.create(link.strip());
			for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
				if (!isPublicHttps(uri)) {
					return Optional.empty();
				}
				HttpRequest request = HttpRequest.newBuilder(uri)
						.timeout(Duration.ofSeconds(8))
						.header("User-Agent", "Mozilla/5.0 (Bill Locker link check)")
						.GET()
						.build();
				HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
				int status = response.statusCode();
				if (status >= 300 && status < 400) {
					Optional<String> location = response.headers().firstValue("Location");
					if (location.isEmpty()) {
						return Optional.empty();
					}
					uri = uri.resolve(location.get());
					continue;
				}
				return status < 300 || EXISTS_BUT_BLOCKED.contains(status) ? Optional.of(uri.toString()) : Optional.empty();
			}
			return Optional.empty(); // too many redirects
		} catch (IllegalArgumentException | IOException e) {
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	/** https, a real host name, and every address it resolves to is on the public internet. */
	static boolean isPublicHttps(URI uri) {
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
			return false;
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
			return false;
		}
		try {
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
						|| address.isAnyLocalAddress() || address.isMulticastAddress()
						|| isUniqueLocalV6(address)) {
					return false;
				}
			}
			return true;
		} catch (UnknownHostException e) {
			return false; // a made-up domain
		}
	}

	/** IPv6 fc00::/7, the IPv6 version of a private network. */
	private static boolean isUniqueLocalV6(InetAddress address) {
		byte[] bytes = address.getAddress();
		return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
	}
}
