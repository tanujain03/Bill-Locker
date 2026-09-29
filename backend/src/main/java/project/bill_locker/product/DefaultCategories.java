package project.bill_locker.product;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts the standard categories on startup if they are missing.
 * Idempotent: existing categories (matched by slug) are left untouched.
 */
@Component
@RequiredArgsConstructor
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
