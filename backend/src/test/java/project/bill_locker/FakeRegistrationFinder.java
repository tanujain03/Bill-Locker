package project.bill_locker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import project.bill_locker.document.registration.RegistrationFinder;

/**
 * Stands in for the Gemini + Google Search lookup in tests: never goes online. By default
 * it "finds" https://<brand>.example/warranty-registration; a test can make it find nothing.
 * It remembers which brands it was asked about, so tests can check "one search per brand".
 */
public class FakeRegistrationFinder implements RegistrationFinder {

	private final List<String> searchedBrands = new ArrayList<>();
	private boolean findNothing;

	@Override
	public synchronized Optional<String> find(String brand, String productName) {
		searchedBrands.add(brand);
		return findNothing ? Optional.empty()
				: Optional.of("https://" + brand.toLowerCase().replaceAll("[^a-z0-9]", "") + ".example/warranty-registration");
	}

	public synchronized List<String> searchedBrands() {
		return List.copyOf(searchedBrands);
	}

	public synchronized void findNothing() {
		this.findNothing = true;
	}

	public synchronized void reset() {
		searchedBrands.clear();
		findNothing = false;
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary // wins over the real Gemini finder
		FakeRegistrationFinder fakeRegistrationFinder() {
			return new FakeRegistrationFinder();
		}
	}
}
