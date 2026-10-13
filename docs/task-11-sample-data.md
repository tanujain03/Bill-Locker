# Task 11 — Sample data button

After this task, an empty account can be filled with realistic demo bills in one click, so
every page has something to show (for a first look, or for a demo to judges):

- **"Try with sample data"** on the dashboard's welcome screen (an account without bills).
- **Settings → Sample data**: add the demo bills, or remove them.
- While demo bills are in the account, the **dashboard shows a violet banner** ("You're exploring
  with sample data") with **Remove sample data**.
- Every demo bill has a violet **"Sample"** badge (flask icon) in place of "Uploaded", in the
  Documents list, on the dashboard's recent bills and on the bill's own page.
- **Removing deletes only the demo bills.** Bills the user added stay (asked first, with the
  app's confirm dialog).

## 1. What gets added (`sample/SampleBills.java`)

About 30 saved bills, **dated from today**, so whenever the button is pressed the story is the same:

| Bill | Shows |
|---|---|
| Croma: Samsung Galaxy S25 (9 months ago, 12 months warranty, registration link) | Active, about 3 months left; a complete claim kit |
| Reliance Digital: LG refrigerator (2 years warranty ending in about 18 days) | **Expiring soon** (dashboard + 🔔) |
| Amazon: boAt headphones (13 months ago, 12 months) | **Expired** about a month ago |
| Vijay Sales: Dell laptop + Logitech mouse (4 months ago) | Two products on one bill, both active |
| Flipkart: Philips air fryer (about 6 weeks ago, 24 months) | Active |
| Decathlon: running shoes (no warranty details) | **No info** |
| Airtel ₹599, Netflix ₹649, BESCOM electricity (~₹2,000), BigBasket every month for 3 months | **Recurring payments** on the Expenses page |
| Swiggy, Uber, Indian Oil, Apollo Pharmacy, PVR at uneven amounts | Food, travel, fuel, health and entertainment categories (deliberately *not* recurring) |

Receipts whose day of the month hasn't come yet are skipped (no bills in the future), so the
exact count depends on the date (about 29–31). Warranty end dates are worked out from start +
months exactly like a normal save. Seller names are real Indian shops to look familiar; every
bill is marked as sample and its PDF says "SAMPLE BILL – not a real purchase".

## 2. The PDF (`sample/SamplePdf.java`)

Each bill gets a one-page PDF drawn with PDFBox (already in the project for QR codes): the
"SAMPLE BILL" line, the shop, type, number and date, each product with model, serial number and
warranty, the GST and the total. So the bill page can preview it and "Download invoice" in the
claim kit works. The built-in PDF fonts have no ₹ sign, so the PDF writes "Rs.".

## 3. Backend

| Piece | Job |
|---|---|
| `documents.sample` (`Document.sample`, a `Boolean`) | Marks a demo bill. A nullable Boolean so Hibernate can add the column to a table with rows (null = false). |
| `DocumentSummary.sample`, `DocumentDetail.sample` | The flag for the badge |
| `DocumentService.createSample` | Stores one demo bill: saved straight away, end dates filled in, flagged |
| `DocumentService.deleteSamples` / `DocumentRepository.deleteSamples` | One `delete … where sample = true` for the user; the database removes files and products (`ON DELETE CASCADE`) and unlinks Gmail files (`SET NULL`) |
| `SampleDataService` | Adds all bills in **one transaction** (all or nothing); a second add → `409 SAMPLE_DATA_EXISTS` |
| `SampleDataController` | `GET` (how many), `POST` (add), `DELETE` (remove) on `/api/sample-data` |

## 4. Frontend

| File | Job |
|---|---|
| `lib/sample-data.ts` | The three calls |
| `lib/useSampleData.ts` | Count, add (toast), remove (confirm, toast), then reload the page's numbers; used by the dashboard and Settings |
| `pages/DashboardPage.tsx` | "Try with sample data" on the welcome screen; the violet banner |
| `pages/SettingsPage.tsx` | The "Sample data" section |
| `components/documents/SourceBadge.tsx` | The "Sample" badge (Documents list, recent bills, bill page) |

## 5. Tests

`backend/src/test/java/project/bill_locker/sample/SampleDataApiTests.java` ("today" = 7 Oct 2026):
adding gives at least 25 saved, flagged bills, counted on the dashboard, with warranties in every
state (1 expiring soon, 1 expired, 4 active, 1 no info) and Airtel, Netflix and electricity as
recurring payments; a sample bill downloads as a real PDF; adding twice is a 409; removing keeps
the user's own bill and another user's sample data, and the data can be added again; sign-in required.
