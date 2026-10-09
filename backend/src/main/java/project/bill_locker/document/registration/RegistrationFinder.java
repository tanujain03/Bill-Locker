package project.bill_locker.document.registration;

import java.util.Optional;

/**
 * Finds a brand's official warranty registration page on the web, for bills that don't
 * print one. The real one asks Gemini with Google Search ({@link GeminiRegistrationFinder});
 * tests use a fake, so they never go online.
 */
public interface RegistrationFinder {

	/**
	 * @param brand       e.g. "Noise"
	 * @param productName e.g. "Noise Buds VS104", to pick the right page for brands with several
	 * @return the page's address, checked that it opens; empty when nothing reliable was found
	 */
	Optional<String> find(String brand, String productName);
}
