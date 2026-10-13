package project.bill_locker.sample;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import project.bill_locker.document.BillCategory;
import project.bill_locker.document.DocumentDetails;
import project.bill_locker.document.DocumentItemView;
import project.bill_locker.document.DocumentType;
import project.bill_locker.document.RegistrationSource;

/**
 * The demo bills of "Try with sample data". Dates are counted back from today, so whenever
 * the button is pressed the account shows the same story:
 * <ul>
 * <li>Warranties: a phone with about 3 months left, a fridge that ends in a few weeks
 * (expiring soon), headphones that ended last month (expired), a laptop and a mouse,
 * an air fryer, and running shoes without warranty details (no info).</li>
 * <li>Bills and receipts over the last 3 months: Airtel, Netflix and electricity every month
 * (so they show as recurring payments), plus food, rides, fuel, groceries, a pharmacy and a
 * cinema.</li>
 * </ul>
 */
final class SampleBills {

	/** One sample bill: the file name it gets, and its details. */
	record SampleBill(String fileName, DocumentDetails details) {
	}

	private SampleBills() {
	}

	static List<SampleBill> forToday(LocalDate today) {
		List<SampleBill> bills = new ArrayList<>();

		// ---- Products with a warranty (invoices) ----
		LocalDate phoneDay = today.minusMonths(9).minusDays(3);
		bills.add(invoice("Croma", "CR-24117", phoneDay, BillCategory.ELECTRONICS, "12203.00",
				product("Samsung Galaxy S25 (256 GB)", "Samsung", "SM-S931B", "R5CX21A9K3L", "79999.00", 12, phoneDay,
						"Samsung India", "https://www.samsung.com/in/support/your-service/register-product/")));

		LocalDate fridgeDay = today.minusMonths(24).plusDays(18); // a 2-year warranty ending in about 18 days
		bills.add(invoice("Reliance Digital", "RD-88213", fridgeDay, BillCategory.APPLIANCES, "4346.00",
				product("LG 260 L Frost-Free Refrigerator", "LG", "GL-T292RPZY", "412KRWX0Q88", "28490.00", 24, fridgeDay,
						"LG Electronics India", null)));

		LocalDate headphonesDay = today.minusMonths(13); // 12 months: ended about a month ago
		bills.add(invoice("Amazon", "AMZ-406-7781", headphonesDay, BillCategory.ELECTRONICS, "229.00",
				product("boAt Rockerz 450 Headphones", "boAt", "Rockerz 450", null, "1499.00", 12, headphonesDay,
						"boAt", null)));

		LocalDate laptopDay = today.minusMonths(4).minusDays(6);
		bills.add(invoice("Vijay Sales", "VS-55602", laptopDay, BillCategory.ELECTRONICS, "9120.00",
				product("Dell Inspiron 15 3530 Laptop", "Dell", "Inspiron 15 3530", "7HQ9XK3", "58990.00", 12, laptopDay,
						"Dell India", null),
				product("Logitech M331 Silent Mouse", "Logitech", "M331", null, "799.00", 12, laptopDay, "Logitech", null)));

		LocalDate fryerDay = today.minusMonths(1).minusDays(10);
		bills.add(invoice("Flipkart", "FK-OD3301", fryerDay, BillCategory.APPLIANCES, "1144.00",
				product("Philips HD9252 Air Fryer", "Philips", "HD9252/90", "AF2290017", "7499.00", 24, fryerDay,
						"Philips India", null)));

		LocalDate shoesDay = today.minusMonths(2).minusDays(4);
		bills.add(invoice("Decathlon", "DC-11873", shoesDay, BillCategory.FASHION, "428.00",
				product("Kalenji Jogflow 500 Running Shoes", "Kalenji", null, null, "3999.00", null, null, null, null)));

		// ---- Everyday bills and receipts, the last 3 months ----
		String[] power = {"1960.00", "2105.00", "1840.00"}; // this month, last month, the month before
		String[][] food = {{"450.00", "380.00"}, {"820.00", "240.00"}, {"410.00"}}; // uneven, like real orders: not "recurring"
		String[] groceries = {"2980.00", "3200.00", "2750.00"};
		for (int m = 0; m < 3; m++) {
			receipt(bills, today, m, 4, "Airtel Postpaid", "AIR", BillCategory.PHONE_INTERNET, "599.00");
			receipt(bills, today, m, 1, "Netflix", "NFX", BillCategory.ENTERTAINMENT, "649.00");
			receipt(bills, today, m, 7, "BESCOM Electricity", "BES", BillCategory.UTILITIES, power[m]);
			receipt(bills, today, m, 5, "BigBasket", "BB", BillCategory.GROCERIES, groceries[m]);
			for (int i = 0; i < food[m].length; i++) {
				receipt(bills, today, m, 3 + 9 * i, "Swiggy", "SWG", BillCategory.FOOD, food[m][i]);
			}
		}
		receipt(bills, today, 0, 2, "Uber", "UBR", BillCategory.TRAVEL, "320.00");
		receipt(bills, today, 1, 18, "Uber", "UBR", BillCategory.TRAVEL, "540.00");
		receipt(bills, today, 2, 22, "Uber", "UBR", BillCategory.TRAVEL, "275.00");
		receipt(bills, today, 0, 6, "Indian Oil", "IOC", BillCategory.FUEL, "2500.00");
		receipt(bills, today, 1, 14, "Indian Oil", "IOC", BillCategory.FUEL, "1200.00"); // uneven fill-ups: not "recurring"
		receipt(bills, today, 1, 21, "Apollo Pharmacy", "APL", BillCategory.HEALTH, "860.00");
		receipt(bills, today, 2, 12, "PVR Cinemas", "PVR", BillCategory.ENTERTAINMENT, "780.00");
		return bills;
	}

	private static DocumentItemView product(String name, String brand, String model, String serial, String price,
			Integer months, LocalDate start, String provider, String registrationUrl) {
		return new DocumentItemView(name, model, serial, new BigDecimal(price), months, months == null ? null : start, null,
				provider, brand, registrationUrl, registrationUrl == null ? null : RegistrationSource.DOCUMENT);
	}

	private static SampleBill invoice(String seller, String number, LocalDate date, BillCategory category, String tax,
			DocumentItemView... items) {
		BigDecimal total = BigDecimal.ZERO;
		for (DocumentItemView item : items) {
			total = total.add(item.unitPrice());
		}
		return new SampleBill(fileName(seller, number), new DocumentDetails(DocumentType.INVOICE, number, seller, null,
				null, null, null, null, date, new BigDecimal(tax), total, List.of(items), category));
	}

	/** A receipt on day {@code day} of the month {@code monthsAgo} months back; skipped if that day is still to come. */
	private static void receipt(List<SampleBill> bills, LocalDate today, int monthsAgo, int day, String seller,
			String prefix, BillCategory category, String amount) {
		LocalDate month = today.minusMonths(monthsAgo);
		LocalDate date = month.withDayOfMonth(Math.min(day, month.lengthOfMonth()));
		if (date.isAfter(today)) {
			return;
		}
		String number = prefix + "-" + date.toString().replace("-", "") + "-" + day;
		bills.add(new SampleBill(fileName(seller, number), new DocumentDetails(DocumentType.RECEIPT, number, seller, null,
				null, null, null, null, date, null, new BigDecimal(amount), List.of(), category)));
	}

	private static String fileName(String seller, String number) {
		return "sample-" + seller.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + number.toLowerCase() + ".pdf";
	}
}
