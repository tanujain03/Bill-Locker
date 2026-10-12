# Task 4 — Dashboard + Warranties page (design)

Date: 2026-10-07 · Branch: `restart` · Builds on task 2 (documents) and task 3 (Gmail import).

## 1. Goal

After signing in, the user lands on a professional, interactive **dashboard** that shows at
a glance how their bills stand: totals, what needs attention, warranty health, what expires
soon, spending over time, top shops, recent bills and Gmail status. Every number or row
leads to the matching list. A new **Warranties** page lists every product's warranty with
its status. Simple to read, nothing existing breaks, no new tables, no new libraries.

**Done when:** the dashboard and warranties page show correct numbers for saved bills,
every card/segment/bar/row navigates to the right filtered list, empty and loading states
look intentional, it works at 375 px, and all backend tests pass.

## 2. Decisions (agreed with the user)

| Topic | Decision |
|---|---|
| Scope | Dashboard + Warranties page. No Product/Warranty tables (they live in saved bills' product lines) |
| Content | Summary cards; warranty health + expiring soon; spending chart + top shops; recent bills + Gmail card |
| Charts | Hand-made with HTML/Tailwind (+ a little SVG if needed); no chart library |
| Counting | Only **saved** bills count for money, products and warranties; unsaved ones appear under "Needs your attention" |
| Approach | **A:** `GET /api/dashboard` and `GET /api/warranties` compute everything on the backend; the frontend only draws |
| Bug fix | Saving a bill fills an empty warranty end date (start + months − 1 day), like an AI read already does |

Out of scope: product/warranty editing outside the bill form, categories, reminders,
notifications, currencies (amounts keep today's `41,299.00` format, no symbol).

## 3. Rules (one place: `warranty/WarrantyRules`)

- **Effective end date** of a product line = its `warrantyEndDate`, or else
  `warrantyStartDate + warrantyPeriodMonths − 1 day` when both are set, or else none.
- **Status** (with `today` from an injected `Clock`):
  - `NO_INFO` — no effective end date
  - `EXPIRED` — end < today
  - `EXPIRING_SOON` — today ≤ end ≤ today + 30 days
  - `ACTIVE` — end > today + 30 days
- **days left** = end − today in days (negative when expired; null for `NO_INFO`).
- Only product lines of documents with status `SAVED` count.

## 4. Backend

New package `project.bill_locker.warranty` and `project.bill_locker.dashboard`. Same
conventions as before (controller → service → repository, records out, user id from the
token, `findBy…UserId`). Computation in Java over the user's saved documents loaded with
their items in one query (`left join fetch d.items`, `status = SAVED`); the data per user is
small.

### 4.1 `GET /api/warranties?status=&q=`
- `status`: `ACTIVE | EXPIRING_SOON | EXPIRED | NO_INFO`, optional (all when absent); bad
  value → 400.
- `q`: optional, case-insensitive substring over product name, model, serial, seller,
  warranty provider.
- Answer `{ counts: { all, active, expiringSoon, expired, noInfo }, items: WarrantyView[] }`.
  `counts` ignore `status` but respect `q` (so tab counts match the search).
- `WarrantyView { documentId, productName, modelNumber, serialNumber, sellerName,
  warrantyProvider, purchaseDate, startDate, endDate, daysLeft, status }` (`endDate` is the
  effective end date).
- Order: `EXPIRING_SOON` and `ACTIVE` by end date ascending; `EXPIRED` by end date
  descending (most recent first); `NO_INFO` by purchase date descending. In "All": expiring
  soon, active, expired, no info — each group in its own order.

### 4.2 `GET /api/dashboard`
```json
{
  "savedBills": 24, "savedBillsThisMonth": 4,
  "products": 31, "productsWithWarranty": 27,
  "totalSpent": 412350.00, "spentThisMonth": 38200.00,
  "attention": { "toReview": 2, "readFailed": 1, "reading": 0 },
  "warranties": { "active": 18, "expiringSoon": 3, "expired": 6, "noInfo": 4 },
  "expiringSoon": [ /* WarrantyView[], the 5 nearest EXPIRING_SOON, soonest first */ ],
  "spendingByMonth": [ { "month": "2025-11", "amount": 0, "bills": 0 }, "… 12 entries, oldest first, current month last" ],
  "billsWithoutDateOrTotal": 2,
  "topShops": [ { "name": "Croma", "amount": 120000.00, "bills": 5 } ],
  "recentBills": [ /* DocumentSummary[], the 5 newest by createdAt, any status */ ]
}
```
- "this month" = purchase date in the current calendar month (`savedBillsThisMonth`,
  `spentThisMonth`).
- `products` = product lines on saved bills; `productsWithWarranty` = those not `NO_INFO`.
- `totalSpent` = sum of `totalAmount` of saved bills (null totals skipped).
- `attention` (all the user's documents, any status): `toReview` = `EXTRACTED`;
  `readFailed` = `UPLOADED` with `readError` set and not queued; `reading` = queued
  (`readQueuedAt` set).
- `spendingByMonth`: always 12 entries (zeros included) for the current month and the 11
  before it, by purchase date; saved bills only.
- `billsWithoutDateOrTotal`: saved bills missing a purchase date or a total (not in the chart).
- `topShops`: up to 5, saved bills with a seller and a total, grouped by seller name
  trimmed and lower-cased (shown with the most recent spelling), by amount descending.
- An account with no documents answers zeros/empty lists (never 404).

### 4.3 Save fix
`DocumentService.save` applies the same `withWarrantyEndDates` as the AI read before
storing: an item with start + months and no end gets end = start + months − 1 day. The
existing "end before start" validation still runs on what the user typed.

### 4.4 `Clock`
A `@Bean Clock clock()` (system default zone) in a small config; services use
`LocalDate.now(clock)`. Tests replace it with a fixed clock.

## 5. Frontend

- `AppHeader`: links **Dashboard** (`/home`) · Documents · **Warranties** · Gmail.
- `pages/DashboardPage.tsx` replaces `HomePage.tsx` at `/home` (the route stays).
- Components in `components/dashboard/`: `StatCard`, `WarrantyHealth` (stacked bar +
  legend), `ExpiringList`, `SpendingChart` (12 bars), `TopShops` (horizontal bars),
  `RecentBills`, `GmailCard`. `components/warranties/WarrantyStatusPill.tsx` (icon + label +
  colour; shared by the dashboard and the warranties page).
- `lib/dashboard.ts`, `lib/warranties.ts`: types mirroring the records + one function per
  endpoint (through `lib/api.ts`). The Gmail card uses the existing
  `GET /api/integrations/gmail` overview (`lib/gmail.ts`); if Gmail isn't configured the
  card says so briefly.
- **Header row:** greeting by time of day ("Good morning/afternoon/evening, {first name}"),
  a subtitle, and two buttons: **Upload a bill** (→ `/documents`) and **Import from Gmail**
  (→ `/gmail`).
- **Summary cards** (icon, big number, small sub-line, whole card is a link):
  Bills saved → `/documents?status=SAVED`; Products → `/warranties`; Total spent → scrolls
  to the spending chart; Needs your attention (amber when > 0, sub-line "2 to review ·
  1 failed · 7 in Gmail") → `/documents?status=EXTRACTED` (or `/gmail` when only Gmail files
  wait).
- **Warranty health:** one stacked bar (segments sized by count, min width so small groups
  stay visible) + legend with counts; each segment and legend item links to
  `/warranties?status=…`. Colours: active emerald, expiring amber, expired rose, no info
  slate — always with icon/label.
- **Expiring soon:** up to 5 rows "N days left · product · shop" → `/documents/{id}`;
  "View all" → `/warranties?status=EXPIRING_SOON`; empty: "Nothing expires in the next
  30 days".
- **Spending chart:** 12 vertical bars scaled to the max, month labels (`Nov`… with the year
  on January and on the first bar), a tooltip on hover **and** keyboard focus ("Sep 2026 ·
  38,200.00 · 4 bills"); each bar links to `/documents?month=2026-09`; footnote when
  `billsWithoutDateOrTotal > 0`; empty: "No spending yet — save a bill with a date and total".
- **Top shops:** up to 5 rows name + amount + a proportional bar → `/documents?q=<name>`.
- **Recent bills:** 5 rows with `FileTypeIcon`, title, type · date, `SourceBadge`,
  `StatusBadge` → the bill; "View all" → `/documents`.
- **Gmail card:** accounts connected, files to review, last scan ("2 h ago"); buttons
  **Review files** / **Connect Gmail**.
- **First run** (no documents at all): one welcome panel instead of the grid: "Upload your
  first bill or connect Gmail" with both buttons.
- **Loading:** skeleton blocks in the grid's shape. **Error:** `Alert` + **Try again**.
- **Layout:** 4 cards in a row on desktop, 2 on tablets, 1 on phones; the other sections in
  a 2-column grid on desktop, 1 column below `lg`.
- **Documents page:** its filters are read from and written to the URL (`?q=&type=&status=
  &source=&month=`), so dashboard links land pre-filtered and the back button works.
  `month` (YYYY-MM) filters by purchase date in the browser, like `source`, with a small
  removable chip "Sep 2026 ×".
- **Warranties page (`/warranties`):** title + one-line explanation; tabs with counts (All ·
  Active · Expiring soon · Expired · No info) synced to `?status=`; search box synced to
  `?q=` (300 ms debounce); a table on `md+` (Product · Shop · Ends · Days left · Bill) and
  cards below; `WarrantyStatusPill` with "12 days left" / "Expired 3 days ago" / "No end
  date"; rows link to the bill; empty states per tab and for "no saved bills yet".

## 6. Errors
`400 VALIDATION_FAILED`-style answer for a bad `status` (whatever `GlobalExceptionHandler`
gives for an unconvertible enum — must be 400, not 500). No new error codes.

## 7. Tests (`mvnw test`)
- `WarrantyRulesTests` (unit, fixed dates): effective end from end / from start+months /
  none; status boundaries at end = today − 1, today, today + 30, today + 31.
- `WarrantyApiTests`: only saved bills; status filter; `q` filter; counts respect `q` but
  not `status`; ordering per group; bad status → 400; other user's bills absent.
- `DashboardApiTests`: empty account → zeros; saved vs unsaved counting; attention counts;
  12 spending months with zeros, oldest first, current last, bills outside 12 months
  excluded; this-month numbers; top shops grouping (case/space-insensitive) and order;
  `billsWithoutDateOrTotal`; recent bills any status, newest first; other user's data absent.
- `DocumentSaveApiTests`: saving start + months without end → end filled
  (2026-01-10 + 12 → 2027-01-09).
- Tests use a fixed `Clock` (a `@TestConfiguration` `@Primary` bean).
- Frontend: `npm run typecheck`, `npm run build`, manual browser check (desktop + 375 px).

## 8. Docs
`docs/task-4-dashboard.md` (guide in the task-2/3 style: what happens where, the rules,
endpoints, try it, tests), `docs/api-contract.md` (dashboard + warranties sections, top
list), `CLAUDE.md` (Task 4 entry).
