package project.bill_locker.document;

import java.util.Locale;

/**
 * What a bill was spent on. Every document can have one: receipts (rides, food…) and
 * invoices (a phone is ELECTRONICS, a fridge APPLIANCES). The Expenses page adds money up
 * per category; the Bills & receipts page filters on it. Stored as plain text (not a
 * database enum), so adding a category later needs no database change.
 */
public enum BillCategory {
	TRAVEL, FOOD, GROCERIES, FUEL, UTILITIES, PHONE_INTERNET, SHOPPING, HEALTH,
	ELECTRONICS, APPLIANCES, FASHION, HOME, ENTERTAINMENT, OTHER;

	/** The stored text back as a category; anything unknown (or an old value) is OTHER. */
	public static BillCategory parse(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return valueOf(text.strip().toUpperCase(Locale.ROOT).replace(' ', '_').replace('&', '_'));
		} catch (IllegalArgumentException e) {
			return OTHER;
		}
	}
}
