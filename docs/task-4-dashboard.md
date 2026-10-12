# Task 4 — Dashboard and Warranties

After this task:

- **The first page after sign-in is a dashboard** (`/home`): your totals, what needs
  your attention, warranty health, what expires soon, spending per month, top shops,
  recent bills and Gmail status. Every number, bar and row is a link to the matching list.
- **A Warranties page** (`/warranties`) lists every product on your saved bills with its
  warranty status, in tabs (All · Expiring soon · Active · Expired · No info) with a search.
- **The Documents page keeps its filters in the URL** (`?q=&type=&status=&source=&month=`),
  so a dashboard link opens it already filtered, and Back / refresh keep the filters.
- **Saving a bill fills an empty warranty end date** (start + months − 1 day), like an AI
  read already did.

No new tables, no new libraries: the charts are plain HTML bars.

---

## 1. The big picture

```
Browser                                   Backend
DashboardPage ── GET /api/dashboard ────▶ DashboardController → DashboardService
                                             ├─ DocumentRepository.findSavedWithItems(user)  (saved bills + products, 1 query)
                                             ├─ WarrantyService.views(user)                   (status per product)
                                             └─ counts: to review / read failed / reading / 5 newest bills
              ── GET /api/integrations/gmail (existing) ─▶ Gmail card + "in Gmail" count

WarrantiesPage ─ GET /api/warranties?status=&q= ─▶ WarrantyController → WarrantyService
                                                     └─ WarrantyRules (one place for the rules)
```

**Why the backend computes everything:** the rules ("expiring soon = 30 days", "only saved
bills count", "no end date → worked out from start + months") live in one Java class with
tests, so the dashboard and the warranties page can never disagree. The browser only draws.

**Why only saved bills count:** their details were checked by you. A bill the AI read but
you haven't saved could have a misread total. Those are counted under **Needs your
attention** instead, so you know to review them.

## 2. The warranty rules (`warranty/WarrantyRules.java`)

| Step | Rule |
|---|---|
| End date | The printed `warrantyEndDate`; or else `start + months − 1 day` when both are set and months > 0 (10 Jan 2026 + 12 → 9 Jan 2027); or else unknown |
| Status | unknown → **No info**; end < today → **Expired**; end within 0–30 days (today counts) → **Expiring soon**; later → **Active** |
| Days left | end − today (negative once expired; empty when unknown) |

"Today" comes from a `Clock` bean (`common/ClockConfig`), not `LocalDate.now()`: tests pin
it to 7 Oct 2026 (`FixedClockConfig`), so "10 days left" is tested exactly.

## 3. Files

Backend (`backend/src/main/java/project/bill_locker/`):

| File | Job |
|---|---|
| `common/ClockConfig.java` | The clock that says what "today" is |
| `common/GlobalExceptionHandler.java` | Now also: a bad query value (`?status=BAD`) → 400 instead of 500 |
| `warranty/WarrantyStatus.java` | ACTIVE, EXPIRING_SOON, EXPIRED, NO_INFO (worked out, never stored) |
| `warranty/WarrantyRules.java` | The rules above, as three small static methods |
| `warranty/WarrantyView.java`, `WarrantyList.java` | What the warranties page receives |
| `warranty/WarrantyService.java` | Every product of every saved bill → a `WarrantyView`, sorted; search and tab filter |
| `warranty/WarrantyController.java` | `GET /api/warranties` |
| `dashboard/DashboardResponse.java` | The dashboard's answer (records) |
| `dashboard/DashboardService.java` | Totals, this month, attention, warranty counts, 12 months of spending, top shops, recent bills |
| `dashboard/DashboardController.java` | `GET /api/dashboard` |
| `document/DocumentRepository.java` | `findSavedWithItems` (one query with `join fetch`) + the dashboard counts |
| `document/DocumentService.java` | `save` now fills an empty warranty end date |

Frontend (`frontend/src/`):

| File | Job |
|---|---|
| `lib/dashboard.ts`, `lib/warranties.ts` | Types + one function per endpoint; the links the dashboard opens |
| `pages/DashboardPage.tsx` | Greeting, the 4 cards, the 6 sections, first-run welcome, loading/error |
| `components/dashboard/Panel.tsx` | The white card every section sits in |
| `components/dashboard/StatCard.tsx` | One clickable number card |
| `components/dashboard/WarrantyHealth.tsx` | One bar split by status + legend (each part links to that tab) |
| `components/dashboard/ExpiringList.tsx` | The 5 warranties that end soonest |
| `components/dashboard/SpendingChart.tsx` | 12 bars, tooltip on hover **and** keyboard focus, each opens that month's bills |
| `components/dashboard/TopShops.tsx` | Shops by money spent, with proportional bars |
| `components/dashboard/RecentBills.tsx` | The 5 newest bills with source and status badges |
| `components/dashboard/GmailCard.tsx` | Accounts, files to review, last scan |
| `components/warranties/WarrantyStatusPill.tsx` | "12 days left" / "Expired 3 days ago" / "No end date", colour + icon + words |
| `pages/WarrantiesPage.tsx` | Tabs + search (both in the URL), a table on wide screens and cards on phones |
| `pages/DocumentsPage.tsx` | Filters now in the URL; the month filter shows a removable "Bought in Sep 2026" chip |
| `components/AppLayout.tsx`, `UserMenu.tsx`, `ErrorState.tsx` | The frame of every signed-in page: left sidebar (logo, **Upload bill**, Dashboard · Documents · Warranties · Gmail Import, Logout, your name/email) and a top bar (**Upload bill**, account menu with Gmail import and Sign out); below `lg` the sidebar is a ☰ drawer. A parent route in `App.tsx`. `ErrorState` is the "Could not load … / Try again" card. Notifications, reminders, products, services and settings come in later tasks |

## 4. Endpoints

`GET /api/warranties?status=&q=` → `{ counts: { all, active, expiringSoon, expired, noInfo }, items: WarrantyView[] }`.
`status` (optional) narrows the rows only, so every tab keeps its count; `q` searches
product, model, serial, shop and warranty provider, and narrows the counts too.
Order: expiring soon (soonest first), active (soonest first), expired (most recent first),
no info (newest purchase first).

`GET /api/dashboard` → see [`api-contract.md`](api-contract.md) section 7. Highlights:
`spendingByMonth` always has 12 entries (zeros included, current month last), bills with
no date or total aren't on the chart (`billsWithoutDateOrTotal` says how many), and
`topShops` treats "Croma" and "croma " as one shop.

## 5. Where each click goes

| Click | Opens |
|---|---|
| Bills saved | `/documents?status=SAVED` |
| Products | `/warranties` |
| Total spent | scrolls to the spending chart |
| Needs your attention | `/documents?status=EXTRACTED` (or `/gmail` when only Gmail files wait) |
| A warranty-health part or legend item | `/warranties?status=…` |
| An expiring row / recent bill | that bill |
| A spending bar | `/documents?status=SAVED&month=2026-09` |
| A shop | `/documents?status=SAVED&q=Croma` |

The chart and shop links add `status=SAVED` because they only count saved bills: "Sep 2026 ·
4 bills" opens exactly those 4.

## 6. Try it

1. Restart the backend (new endpoints). `npm run dev`, sign in → the dashboard.
2. A new account sees a welcome panel with **Upload a bill** / **Connect Gmail**.
3. Save a bill with a date, total and a product with a warranty end date in the next 30
   days → it appears under **Expiring soon**, the chart gets a bar, the shop appears.
4. Hover a bar (or Tab to it) → its month, amount and number of bills. Click → Documents
   with that month's saved bills and a "Bought in …" chip.
5. Click a colour in **Warranty health** → the warranties page on that tab. Search "lg".
6. Make the window narrow (phone): cards stack, the header shows icons only.

## 7. Tests

| Test class | What it checks |
|---|---|
| `warranty/WarrantyRulesTests` | End date printed / worked out / unknown, 31 Jan + 1 month, 0 months = no info, the boundaries at −1, 0, 30, 31 days |
| `warranty/WarrantyApiTests` | Only saved bills, statuses and days left, worked-out end dates, tab filter keeps all counts, search narrows counts, ordering, bad status → 400, other users' bills absent |
| `dashboard/DashboardApiTests` | Empty account, saved vs unsaved, attention counts, the 12-month window, bills outside it, bills without a date, this month, top shops grouping, products and warranties, recent bills, other users' data absent |
| `document/DocumentSaveApiTests` | `saveFillsEmptyWarrantyEnd` |
| `document/DocumentListApiTests` | `badEnumQueryParamIs400` |

## 8. Known limits

- Amounts have no currency (bills don't record one); they're shown like `41,299.00`.
- Only saved bills count for money, products and warranties.
- The month and "From" filters on Documents work on the loaded list (fine at this size; a
  paged list would move them to the backend).
