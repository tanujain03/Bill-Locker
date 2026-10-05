package project.bill_locker.search;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import project.bill_locker.product.CategoryResponse;
import project.bill_locker.product.ProductResponse;
import project.bill_locker.search.SearchFilters.SortBy;
import project.bill_locker.search.SearchFilters.SortDirection;
import project.bill_locker.warranty.WarrantyStatus;

/**
 * Turns a plain-English question into {@link SearchFilters} with simple rules: the same
 * rules the frontend's demo API uses. The contract plans an AI model for this one step;
 * whatever fills in the filters, running them ({@link SearchService}) stays the same.
 */
@Component
public class SearchInterpreter {

	/** A kind of product and the words people use for it. */
	record Kind(String name, List<String> words) {
	}

	static final List<Kind> KINDS = List.of(
			new Kind("laptop", List.of("laptop", "notebook", "inspiron", "macbook")),
			new Kind("printer", List.of("printer", "deskjet", "smart tank")),
			new Kind("phone", List.of("phone", "mobile", "iphone", "smartphone")),
			new Kind("tv", List.of("tv", "television", "bravia")),
			new Kind("fridge", List.of("fridge", "refrigerator")),
			new Kind("washing machine", List.of("washing machine", "washer")),
			new Kind("ac", List.of("ac", "air conditioner", "aircon", "split ac")),
			new Kind("headphones", List.of("headphone", "earphone", "earbud", "headset")),
			new Kind("watch", List.of("watch", "smartwatch")),
			new Kind("sofa", List.of("sofa", "couch")),
			new Kind("air fryer", List.of("air fryer", "airfryer", "fryer")),
			new Kind("iron", List.of("iron")));

	/** Words that mean a whole category. */
	private record CategoryWord(Pattern words, String slug) {
	}

	private static final List<CategoryWord> CATEGORY_WORDS = List.of(
			new CategoryWord(Pattern.compile("\\bappliances?\\b"), "home-appliances"),
			new CategoryWord(Pattern.compile("\\bkitchen\\b"), "kitchen"),
			new CategoryWord(Pattern.compile("\\bfurniture\\b"), "furniture"),
			new CategoryWord(Pattern.compile("\\b(electronics?|computers?|gadgets?)\\b"), "computers"),
			new CategoryWord(Pattern.compile("\\b(audio|wearables?)\\b"), "audio"),
			new CategoryWord(Pattern.compile("\\b(vehicles?|cars?|bikes?)\\b"), "vehicles"));

	/** Words that carry no meaning for the search ("show me my …"). */
	private static final Set<String> STOP_WORDS = Set.of(("show me my all the a an any find list which what did i do "
			+ "have products product items item purchases purchase bought buy please with of for that whose are is")
			.split(" "));

	private static final Pattern WITHIN_DAYS = Pattern.compile("\\b(?:within|in|next)\\s+(?:the\\s+next\\s+)?(\\d{1,4})\\s*days?\\b");
	private static final Pattern WITHIN_MONTHS = Pattern.compile("\\b(?:within|in|next)\\s+(?:the\\s+next\\s+)?(\\d{1,2})\\s*months?\\b");
	private static final Pattern SELLER = Pattern.compile(
			"\\b(?:from|at|on)\\s+([a-z0-9][a-z0-9.&' -]{1,30}?)(?:\\?|$|\\s+(?:in|during|last|this|for)\\b)");
	private static final Pattern OVER = Pattern.compile(
			"\\b(?:over|above|more than|greater than|costing more than)\\s*(?:rs\\.?|₹|inr)?\\s*([\\d,]+)\\s*(k|l|lakh)?\\b");
	private static final Pattern UNDER = Pattern.compile(
			"\\b(?:under|below|less than|cheaper than)\\s*(?:rs\\.?|₹|inr)?\\s*([\\d,]+)\\s*(k|l|lakh)?\\b");
	private static final Pattern IN_YEAR = Pattern.compile("\\b(?:in|during)\\s+(20\\d{2})\\b");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	/** The rules, one after another. Products are needed to recognise their brands and sellers. */
	public SearchFilters interpret(String query, List<CategoryResponse> categories, List<ProductResponse> products,
			LocalDate today) {
		String q = query.toLowerCase(Locale.ROOT);
		String text = null;
		String categorySlug = null;
		String brand = null;
		String seller = null;
		WarrantyStatus warrantyStatus = null;
		Integer daysUntilExpiry = null;
		LocalDate purchasedAfter = null;
		LocalDate purchasedBefore = null;
		BigDecimal minPrice = null;
		BigDecimal maxPrice = null;
		SortBy sortBy = null;
		SortDirection sortDirection = null;
		Integer limit = null;

		// Warranty: "expiring within 90 days", "expired", "under warranty" …
		Integer days = withinDays(q, today);
		if (days != null && Pattern.compile("expir|warrant|end").matcher(q).find()) {
			daysUntilExpiry = days;
			sortBy = SortBy.WARRANTY_EXPIRY;
			sortDirection = SortDirection.ASC;
		}
		else if (contains(q, "expired|out of warranty")) {
			warrantyStatus = WarrantyStatus.EXPIRED;
		}
		else if (contains(q, "expir\\w* soon|about to expire")) {
			warrantyStatus = WarrantyStatus.EXPIRING_SOON;
			sortBy = SortBy.WARRANTY_EXPIRY;
			sortDirection = SortDirection.ASC;
		}
		else if (contains(q, "under warranty|active warrant|still covered|in warranty")) {
			warrantyStatus = WarrantyStatus.ACTIVE;
		}
		else if (contains(q, "no warranty|unknown warranty|without warranty")) {
			warrantyStatus = WarrantyStatus.UNKNOWN;
		}

		// Seller: "from Croma", only if one of the user's products was bought there.
		String sellerTerm = contains(q, "\\b(from|at)\\b") ? sellerIn(q) : null;
		if (sellerTerm != null && products.stream().anyMatch(product -> sellerMatches(product.seller(), sellerTerm))) {
			seller = capitalise(sellerTerm); // the question was lower-cased to read it: "croma" → "Croma"
		}

		// Brand: one of the user's brands, named in the question.
		brand = products.stream()
				.map(ProductResponse::brand)
				.filter(Objects::nonNull)
				.filter(name -> mentions(q, name) && !(sellerTerm != null && sellerMatches(name, sellerTerm)))
				.findFirst()
				.orElse(null);

		// Category ("appliances") and kind of product ("laptops").
		categorySlug = CATEGORY_WORDS.stream()
				.filter(word -> word.words().matcher(q).find())
				.map(CategoryWord::slug)
				.filter(slug -> categories.stream().anyMatch(category -> category.slug().equals(slug)))
				.findFirst()
				.orElse(null);
		text = KINDS.stream()
				.filter(kind -> kind.words().stream().anyMatch(word -> mentions(q, word)))
				.map(kind -> kind.words().getFirst())
				.findFirst()
				.orElse(null);

		// Price: "over 50k", "under ₹2,000", "below 1 lakh".
		Matcher over = OVER.matcher(q);
		if (over.find()) {
			minPrice = amount(over.group(1), over.group(2));
		}
		Matcher under = UNDER.matcher(q);
		if (under.find()) {
			maxPrice = amount(under.group(1), under.group(2));
		}

		// Purchase date: "in 2025", "last year", "this year".
		Matcher year = IN_YEAR.matcher(q);
		if (year.find()) {
			purchasedAfter = LocalDate.of(Integer.parseInt(year.group(1)), 1, 1);
			purchasedBefore = LocalDate.of(Integer.parseInt(year.group(1)), 12, 31);
		}
		else if (contains(q, "\\blast year\\b")) {
			purchasedAfter = LocalDate.of(today.getYear() - 1, 1, 1);
			purchasedBefore = LocalDate.of(today.getYear() - 1, 12, 31);
		}
		else if (contains(q, "\\bthis year\\b") && daysUntilExpiry == null) {
			purchasedAfter = LocalDate.of(today.getYear(), 1, 1);
		}

		// Order: "most expensive", "cheapest", "latest", "oldest".
		if (contains(q, "most expensive|costliest|priciest|highest price")) {
			sortBy = SortBy.PRICE;
			sortDirection = SortDirection.DESC;
			limit = 1;
		}
		else if (contains(q, "cheapest|least expensive|lowest price")) {
			sortBy = SortBy.PRICE;
			sortDirection = SortDirection.ASC;
			limit = 1;
		}
		else if (contains(q, "\\b(latest|newest|recent)\\b")) {
			sortBy = SortBy.PURCHASE_DATE;
			sortDirection = SortDirection.DESC;
		}
		else if (contains(q, "\\boldest\\b")) {
			sortBy = SortBy.PURCHASE_DATE;
			sortDirection = SortDirection.ASC;
		}

		SearchFilters understood = new SearchFilters(text, categorySlug, brand, seller, warrantyStatus, daysUntilExpiry,
				purchasedAfter, purchasedBefore, minPrice, maxPrice, sortBy, sortDirection, limit);
		if (!understood.equals(NOTHING)) {
			return understood;
		}
		// No rule matched: search for the meaningful words instead ("show me my bosch" → "bosch").
		String words = Arrays.stream(q.replaceAll("[^a-z0-9 ]", " ").split("\\s+"))
				.filter(word -> !word.isBlank() && !STOP_WORDS.contains(word))
				.collect(Collectors.joining(" "));
		return words.isEmpty() ? NOTHING
				: new SearchFilters(words, null, null, null, null, null, null, null, null, null, null, null, null);
	}

	/** No filter at all: every product. */
	private static final SearchFilters NOTHING =
			new SearchFilters(null, null, null, null, null, null, null, null, null, null, null, null, null);

	/** One sentence saying how the question was understood, e.g. "Showing products bought from croma." */
	public String explain(SearchFilters filters, List<CategoryResponse> categories) {
		if (Integer.valueOf(1).equals(filters.limit()) && filters.sortBy() == SortBy.PRICE) {
			return filters.sortDirection() == SortDirection.ASC
					? "Showing your least expensive purchase."
					: "Showing your most expensive purchase.";
		}
		List<String> parts = new ArrayList<>();
		if (filters.text() != null) {
			parts.add("matching “" + filters.text() + "”");
		}
		if (filters.categorySlug() != null) {
			parts.add("in " + categories.stream().filter(category -> category.slug().equals(filters.categorySlug()))
					.map(CategoryResponse::name).findFirst().orElse(filters.categorySlug()));
		}
		if (filters.brand() != null) {
			parts.add("made by " + filters.brand());
		}
		if (filters.seller() != null) {
			parts.add("bought from " + filters.seller());
		}
		if (filters.warrantyStatus() != null) {
			parts.add(switch (filters.warrantyStatus()) {
				case ACTIVE -> "that are under warranty";
				case EXPIRED -> "whose warranty has expired";
				case EXPIRING_SOON -> "whose warranty expires within 30 days";
				case UNKNOWN -> "with no warranty information";
			});
		}
		if (filters.daysUntilExpiry() != null) {
			parts.add("whose warranty expires within the next " + filters.daysUntilExpiry() + " days");
		}
		if (filters.minPrice() != null) {
			parts.add("costing at least " + rupees(filters.minPrice()));
		}
		if (filters.maxPrice() != null) {
			parts.add("costing at most " + rupees(filters.maxPrice()));
		}
		if (filters.purchasedAfter() != null && filters.purchasedBefore() != null) {
			parts.add("bought between " + DATE.format(filters.purchasedAfter()) + " and " + DATE.format(filters.purchasedBefore()));
		}
		else if (filters.purchasedAfter() != null) {
			parts.add("bought after " + DATE.format(filters.purchasedAfter()));
		}
		String sentence = parts.isEmpty() ? "Showing all products" : "Showing products " + String.join(", ", parts);
		if (filters.sortBy() == SortBy.WARRANTY_EXPIRY) {
			sentence += ", soonest expiry first";
		}
		else if (filters.sortBy() == SortBy.PURCHASE_DATE) {
			sentence += filters.sortDirection() == SortDirection.ASC ? ", oldest first" : ", newest first";
		}
		return sentence + ".";
	}

	// ---- Shared with SearchService, which runs the filters ----------------------------

	/** The text filter: the words appear in the product, or it is the same kind ("fridge" finds "Refrigerator"). */
	static boolean matchesText(ProductResponse product, String text) {
		String haystack = String.join(" ", Objects.toString(product.name(), ""), Objects.toString(product.brand(), ""),
				Objects.toString(product.model(), ""), Objects.toString(product.seller(), ""),
				Objects.toString(product.categoryName(), "")).toLowerCase(Locale.ROOT);
		if (haystack.contains(text.toLowerCase(Locale.ROOT))) {
			return true;
		}
		List<String> wanted = kindsOf(text);
		return !wanted.isEmpty()
				&& kindsOf(product.name() + " " + Objects.toString(product.model(), "")).stream().anyMatch(wanted::contains);
	}

	/** "Croma" matches the seller "Croma Retail, Bengaluru": compares letters and digits only. */
	static boolean sellerMatches(String seller, String term) {
		return seller != null && normalise(seller).contains(normalise(term));
	}

	// ---- Small helpers ------------------------------------------------------------------

	static List<String> kindsOf(String text) {
		return KINDS.stream()
				.filter(kind -> kind.words().stream().anyMatch(word -> mentions(text, word)))
				.map(Kind::name)
				.toList();
	}

	/** The word on its own (not inside another word), also as a plural: "laptops" mentions "laptop". */
	static boolean mentions(String text, String word) {
		return Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(word.toLowerCase(Locale.ROOT)) + "(e?s)?($|[^a-z])")
				.matcher(text.toLowerCase(Locale.ROOT)).find();
	}

	private static boolean contains(String text, String regex) {
		return Pattern.compile(regex).matcher(text).find();
	}

	/** "within 90 days" → 90, "in 3 months" → 90, "this month" / "this year" → the days left in it. */
	private static Integer withinDays(String q, LocalDate today) {
		Matcher days = WITHIN_DAYS.matcher(q);
		if (days.find()) {
			return Integer.parseInt(days.group(1));
		}
		Matcher months = WITHIN_MONTHS.matcher(q);
		if (months.find()) {
			return Integer.parseInt(months.group(1)) * 30;
		}
		if (contains(q, "\\bthis month\\b")) {
			return (int) ChronoUnit.DAYS.between(today, today.withDayOfMonth(today.lengthOfMonth()));
		}
		if (contains(q, "\\bthis year\\b")) {
			return (int) ChronoUnit.DAYS.between(today, LocalDate.of(today.getYear(), 12, 31));
		}
		return null;
	}

	private static String sellerIn(String q) {
		Matcher matcher = SELLER.matcher(q);
		return matcher.find() ? matcher.group(1).trim().replaceAll("\\.$", "") : null;
	}

	/** "50k" → 50000, "1 lakh" → 100000. */
	private static BigDecimal amount(String digits, String suffix) {
		BigDecimal value = new BigDecimal(digits.replace(",", ""));
		if ("k".equals(suffix)) {
			return value.multiply(BigDecimal.valueOf(1_000));
		}
		if ("l".equals(suffix) || "lakh".equals(suffix)) {
			return value.multiply(BigDecimal.valueOf(100_000));
		}
		return value;
	}

	/** "reliance digital" → "Reliance Digital". */
	private static String capitalise(String words) {
		return Arrays.stream(words.split(" "))
				.map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
				.collect(Collectors.joining(" "));
	}

	private static String normalise(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/** Indian digit grouping: ₹1,00,000. */
	private static String rupees(BigDecimal amount) {
		return "₹" + NumberFormat.getIntegerInstance(Locale.of("en", "IN")).format(amount);
	}
}
