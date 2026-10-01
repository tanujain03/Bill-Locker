package project.bill_locker.product;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds the standard categories when the app starts, if they are missing. Running it
 * again changes nothing: categories that exist already (same slug) are left alone.
 * The slugs are the ones the frontend has icons for (docs/api-contract.md §4).
 */
@Component
class DefaultCategories implements ApplicationRunner {

	private record Definition(String name, String slug, int sortOrder) {
	}

	private static final List<Definition> DEFAULTS = List.of(
			new Definition("Mobile Phones", "mobile-phones", 10),
			new Definition("Computers & Accessories", "computers", 20),
			new Definition("TV & Entertainment", "tv-entertainment", 30),
			new Definition("Audio & Wearables", "audio", 40),
			new Definition("Home Appliances", "home-appliances", 50),
			new Definition("Kitchen Appliances", "kitchen", 60),
			new Definition("Furniture", "furniture", 70),
			new Definition("Vehicles", "vehicles", 80),
			new Definition("Other", "other", 90));

	private final CategoryRepository categories;

	DefaultCategories(CategoryRepository categories) {
		this.categories = categories;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		Set<String> existing = categories.findAll().stream().map(Category::getSlug).collect(Collectors.toSet());
		DEFAULTS.stream()
				.filter(definition -> !existing.contains(definition.slug()))
				.map(definition -> new Category(definition.name(), definition.slug(), definition.sortOrder()))
				.forEach(categories::save);
	}
}
