# Task 14 — Return-window tracking

After this task, Bill Locker knows until when a purchase can still be returned to the shop, and
warns before that chance is gone:

- **Bill form → Purchase → "Return by"**: a date, or one click on **+7 / +10 / +15 / +30 days**
  (counted from the purchase date; online orders often count from delivery, then pick the exact
  day). **Clear** removes it. The hint under the field says the result in words.
- **The AI reads a printed return policy** ("10 days return", "returnable within 7 days"):
  return by = purchase date + those days. It never guesses one that isn't printed.
- **Bill page and product page**: a banner — "Returnable until 18 Oct 2026 · 5 days left. Keep the
  box, tags and accessories until then." Blue while there's time, **amber in the last 3 days**,
  grey once closed ("Return window closed on …; the warranty still applies").
- **Documents list**: a "Return by 18 Oct" pill while the window is open (amber when closing).
- **Dashboard**: a "Return windows open" strip under the attention banner, closing first
  (at most 3 lines, then "and 2 more"); each line opens its bill.
- **🔔**: "Nike shoes: return within 2 days" for windows closing in 3 days or fewer (today counts).
- **Chat assistant**: `ai_documents.return_by_date`, so "What can I still return?" works.
- **Sample data**: the air fryer is now bought 5 days ago with a 10-day window, so the strip shows it.

## 1. Data

One new nullable column, `documents.return_by_date` (`Document.returnByDate`), filled from
`DocumentDetails.returnByDate`. Hibernate adds it; no reset. `DocumentDetails` got a 14th
component with an extra constructor without it, so existing callers didn't change.

| Where | Rule |
|---|---|
| `PUT /api/documents/{id}` | `returnByDate` (optional). Before the purchase date → `400 VALIDATION_ERROR` with `fieldErrors.returnByDate` "Must be on or after the purchase date." (`DocumentService.checkDates`, next to the warranty check) |
| AI read (`GeminiDetailExtractor` + `GeminiAnswerParser.returnBy`) | schema field `returnWindowDays` (only when printed). Return by = purchase date + days. No purchase date, 0, negative or over 365 days → none |
| `GET /api/dashboard` → `openReturns` | saved bills with a return date **today or later**, soonest first: document id, shop, first product, date, `daysLeft` (0 = today is the last day) |
| `DocumentDetail`, `DocumentSummary` | carry `returnByDate` |

## 2. Frontend

`lib/returns.ts` holds the one rule the browser applies: days left = return date − today
(local date); **closing** = 3 days or fewer (`CLOSING_DAYS`), **closed** = the day has passed;
and the words (`returnText`). Used by:

| File | Job |
|---|---|
| `components/documents/DetailsForm.tsx` | `ReturnWindowField`: date + quick buttons + Clear |
| `components/documents/ReturnWindow.tsx` | `ReturnBadge` (lists) and `ReturnBanner` (bill and product pages) |
| `components/documents/DocumentList.tsx`, `pages/DocumentPage.tsx`, `pages/ProductPage.tsx` | show them |
| `pages/DashboardPage.tsx` | `OpenReturns` strip |
| `lib/useAttention.ts`, `components/NotificationBell.tsx` | closing returns in 🔔 and its count |
| `lib/documents.ts`, `lib/document-form.ts`, `lib/dashboard.ts` | the field and types |

Colour always comes with an icon and words (Undo/Package icons, "2 days left").

## 3. Tests

`ReturnWindowApiTests` ("today" = 7 Oct 2026): a return date is saved and shown on the bill and in
the list; the dashboard lists only open windows, soonest first, including "today is the last day",
and leaves out closed and unknown ones; a date before the purchase is rejected under its field; the
sample data has one open return (the air fryer, 5 days left). `GeminiAnswerParserTests`: "10 days"
on 3 Oct → 13 Oct; no purchase date, 0, or no policy → none.
