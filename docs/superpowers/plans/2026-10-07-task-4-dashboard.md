# Task 4 — Dashboard + Warranties Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A professional, interactive dashboard at `/home` and a Warranties page, both computed on the backend from saved bills, every number linking to the matching filtered list.

**Architecture:** Two read-only endpoints (`GET /api/dashboard`, `GET /api/warranties`) load the user's saved documents with their product lines and compute everything in Java; the warranty rules live in one pure class (`WarrantyRules`) driven by an injected `Clock`. The frontend draws hand-made charts (HTML/Tailwind) and links into the Documents page, whose filters move into the URL.

**Tech Stack:** Spring Boot 4.1.1 / Java 21 / Spring Data JPA; React 19 + TS 5.9 + Tailwind 4 + React Router 7 + lucide-react 1.x. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-07-task-4-dashboard-design.md` (read it with this plan).

## Global Constraints

- No new Maven or npm dependencies; no chart library. No new tables, no new columns, no enum values added to stored columns (`WarrantyStatus` is a response-only enum).
- **Never commit, merge or push** (user rule). Tasks end with a checkpoint.
- Conventions (CLAUDE.md): controller → service (`@Transactional(readOnly = true)` for reads) → repository; records out; user id only from `CurrentUser.id(jwt)`; other users' data never appears.
- Only documents with status `SAVED` count for money, products and warranties. "Expiring soon" = 0–30 days left inclusive. Effective end = `warrantyEndDate` ?? `warrantyStartDate + warrantyPeriodMonths − 1 day` (only when months > 0).
- Amounts keep the existing format (`formatAmount`, `en-IN`, 2 decimals, no currency symbol).
- Frontend: status colour always with icon + label; never render API text as HTML; HTTP only via `lib/api.ts`-based modules; works at 375 px.
- Backend build while IntelliJ may run the app: `./mvnw test -Dmaven.compiler.useIncrementalCompilation=false` (from `backend/`, Docker running). Frontend: `npm run typecheck`, `npm run build` (from `frontend/`).

## Review Focus

1. **A saved bill with a total but no purchase date** — counts in `totalSpent`, not in the chart, counted in `billsWithoutDateOrTotal`. → Task 3 test `billWithoutDateCountsInTotalNotChart`.
2. **Purchase dates outside the 12-month window (older, or in the future)** — not in the chart, still in `totalSpent`. → Task 3 test `spendingOutsideWindowIsExcluded`.
3. **`warrantyPeriodMonths` of 0 (or start without months)** — `NO_INFO`, never "expired yesterday". → Task 1 test `zeroMonthsIsNoInfo`.
4. **Dashboard link numbers must match the list they open** — chart bar and shop links add `status=SAVED`, so "Sep 2026 · 4 bills" opens exactly 4 bills. → Task 5 step (link builders) + Task 4 month filter.
5. **Unknown filter values in URLs** (`/api/documents?status=BAD`, `/api/warranties?status=BAD`) — 400, never 500. → Task 1 test `badEnumQueryParamIs400`.

---

## File map

Backend (`backend/src/main/java/project/bill_locker/`):

| File | Responsibility |
|---|---|
| `common/ClockConfig.java` | `@Bean Clock clock()` = `Clock.systemDefaultZone()` |
| `common/GlobalExceptionHandler.java` (modify) | `MethodArgumentTypeMismatchException` → 400 |
| `document/DocumentService.java` (modify) | `save` fills empty warranty end dates |
| `document/DocumentRepository.java` (modify) | `findSavedWithItems(userId)` |
| `document/DocumentSummary.java` (modify) | `of(Document)` becomes `public` |
| `warranty/WarrantyStatus.java`, `WarrantyRules.java`, `WarrantyView.java`, `WarrantyList.java`, `WarrantyService.java`, `WarrantyController.java` | warranty rules + `GET /api/warranties` |
| `dashboard/DashboardResponse.java`, `DashboardService.java`, `DashboardController.java` | `GET /api/dashboard` |

Tests: `FixedClockConfig.java` (new, imported by `ApiTest`), `warranty/WarrantyRulesTests.java`, `warranty/WarrantyApiTests.java`, `dashboard/DashboardApiTests.java`, `document/DocumentSaveApiTests.java` (add), `document/DocumentListApiTests.java` (add).

Frontend (`frontend/src/`): `lib/warranties.ts`, `lib/dashboard.ts`, `components/warranties/WarrantyStatusPill.tsx`, `pages/WarrantiesPage.tsx`, `pages/DashboardPage.tsx` (replaces `pages/HomePage.tsx`), `components/dashboard/{StatCard,WarrantyHealth,ExpiringList,SpendingChart,TopShops,RecentBills,GmailCard}.tsx`; modify `pages/DocumentsPage.tsx`, `components/AppHeader.tsx`, `App.tsx`.

Docs: `docs/task-4-dashboard.md`, `docs/api-contract.md`, `CLAUDE.md`.

---

### Task 1: Clock, warranty rules, save fix, 400 for bad enum params

**Files:**
- Create: `common/ClockConfig.java`, `warranty/WarrantyStatus.java`, `warranty/WarrantyRules.java`, test `FixedClockConfig.java`, test `warranty/WarrantyRulesTests.java`
- Modify: `common/GlobalExceptionHandler.java`, `document/DocumentService.java`, test `ApiTest.java`, tests `document/DocumentSaveApiTests.java`, `document/DocumentListApiTests.java`

**Interfaces:**
- Produces:
  - `public enum WarrantyStatus { ACTIVE, EXPIRING_SOON, EXPIRED, NO_INFO }`
  - `public final class WarrantyRules` (static, no Spring): `static final int SOON_DAYS = 30;` `static LocalDate effectiveEnd(LocalDate end, LocalDate start, Integer months)` (null when unknown; computed only when `start != null && months != null && months > 0`); `static WarrantyStatus status(LocalDate effectiveEnd, LocalDate today)`; `static Long daysLeft(LocalDate effectiveEnd, LocalDate today)` (null when end null).
  - `ClockConfig`: `@Bean Clock clock()`.
  - Test `FixedClockConfig` (`@TestConfiguration`, `@Bean @Primary Clock fixedClock()` = `Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC)`), constant `public static final LocalDate TODAY = LocalDate.of(2026, 10, 7)`; added to `ApiTest`'s `@Import`.
  - `GlobalExceptionHandler`: `MethodArgumentTypeMismatchException` → 400 `VALIDATION_ERROR` with message "Please check the filters." and `fieldErrors = { <param name>: "Not a valid value." }`.
  - `DocumentService.save` applies the existing `withWarrantyEndDates` to the request's details **after** `checkWarrantyDates` and before `replaceDetails`.

- [ ] **Step 1: Write failing tests.**

`WarrantyRulesTests` (plain JUnit, fixed `today = 2026-10-07`):
```java
@Test void endDateWins()                 // effectiveEnd(2027-01-01, 2026-01-01, 12) == 2027-01-01
@Test void startPlusMonthsMinusOneDay()  // effectiveEnd(null, 2026-01-10, 12) == 2027-01-09
@Test void endOfMonthClamps()            // effectiveEnd(null, 2026-01-31, 1) == 2026-02-27  (Feb 28 − 1 day)
@Test void zeroMonthsIsNoInfo()          // effectiveEnd(null, 2026-01-10, 0) == null; status(null, today) == NO_INFO
@Test void startWithoutMonthsIsNoInfo()  // effectiveEnd(null, 2026-01-10, null) == null
@Test void boundaries()                  // status(today−1)=EXPIRED, status(today)=EXPIRING_SOON, status(today+30)=EXPIRING_SOON, status(today+31)=ACTIVE
@Test void daysLeft()                    // daysLeft(today+12)=12, daysLeft(today−3)=−3, daysLeft(null)=null
```
`DocumentSaveApiTests`: `saveFillsEmptyWarrantyEnd` — PUT an item with `warrantyStartDate 2026-01-10`, `warrantyPeriodMonths 12`, no end → response `items[0].warrantyEndDate == "2027-01-09"`; and GET returns the same.
`DocumentListApiTests`: `badEnumQueryParamIs400` — `GET /api/documents?status=BAD` → 400, `$.code == "VALIDATION_ERROR"`.

- [ ] **Step 2: Run, expect FAIL.** `./mvnw test -Dtest=WarrantyRulesTests,DocumentSaveApiTests,DocumentListApiTests -Dmaven.compiler.useIncrementalCompilation=false`
- [ ] **Step 3: Implement** the files above. Update the comment above `withWarrantyEndDates` to say it runs on AI reads and on save.
- [ ] **Step 4: Run the full suite, expect PASS.**
- [ ] **Step 5: Checkpoint.**

---

### Task 2: `GET /api/warranties`

**Files:**
- Create: `warranty/WarrantyView.java`, `warranty/WarrantyList.java`, `warranty/WarrantyService.java`, `warranty/WarrantyController.java`, test `warranty/WarrantyApiTests.java`
- Modify: `document/DocumentRepository.java`

**Interfaces:**
- Consumes: Task 1 `WarrantyRules`, `WarrantyStatus`, `Clock`.
- Produces:
  - `DocumentRepository.findSavedWithItems(UUID userId): List<Document>` — `select distinct d from Document d left join fetch d.items where d.user.id = :userId and d.status = DocumentStatus.SAVED`.
  - `record WarrantyView(UUID documentId, String productName, String modelNumber, String serialNumber, String sellerName, String warrantyProvider, LocalDate purchaseDate, LocalDate startDate, LocalDate endDate, Long daysLeft, WarrantyStatus status)` — `endDate` is the effective end.
  - `record WarrantyList(Counts counts, List<WarrantyView> items)`, `record Counts(long all, long active, long expiringSoon, long expired, long noInfo)` (nested in `WarrantyList`).
  - `WarrantyService.views(UUID userId): List<WarrantyView>` — every item of every saved bill, sorted as spec §4.1 "All" order. Public: Task 3 reuses it.
  - `WarrantyService.list(UUID userId, WarrantyStatus status, String q): WarrantyList` — `q` (trimmed, case-insensitive substring over productName, modelNumber, serialNumber, sellerName, warrantyProvider) filters both counts and items; `status` filters items only.
  - `GET /api/warranties` with optional `status` (enum) and `q`.
  - Sort comparator (one static method): group order EXPIRING_SOON, ACTIVE, EXPIRED, NO_INFO; within EXPIRING_SOON/ACTIVE end ascending; EXPIRED end descending; NO_INFO purchaseDate descending (nulls last), then productName.

- [ ] **Step 1: Write failing tests** `WarrantyApiTests extends DocumentApiTestBase` (create bills via upload + `PUT /api/documents/{id}` with the needed items; `TODAY = 2026-10-07` from Task 1):
```java
@Test void onlySavedBillsCount()          // an EXTRACTED bill's items (use FakeDetailExtractor + POST extract) are absent; saved bill's items present
@Test void statusesAndDaysLeft()          // items ending 2026-10-17 (EXPIRING_SOON, 10), 2027-10-07 (ACTIVE), 2026-09-01 (EXPIRED, −36), no dates (NO_INFO, null)
@Test void statusFilterKeepsAllCounts()   // ?status=EXPIRED → items only expired; counts.all == 4, counts.expired == 1
@Test void searchFiltersCountsToo()       // ?q=samsung matches provider/product case-insensitively; counts.all reflects the search
@Test void orderingAllTab()               // expiring, active, expired, no info
@Test void expiredMostRecentFirst()       // two expired: 2026-09-30 before 2026-01-01
@Test void badStatusIs400()               // ?status=BAD → 400
@Test void otherUsersBillsAbsent()
@Test void emptyAccount()                 // counts all zero, items []
```
- [ ] **Step 2: Run, expect FAIL.** `./mvnw test -Dtest=WarrantyApiTests -Dmaven.compiler.useIncrementalCompilation=false`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the full suite, expect PASS.**
- [ ] **Step 5: Checkpoint.**

---

### Task 3: `GET /api/dashboard`

**Files:**
- Create: `dashboard/DashboardResponse.java`, `dashboard/DashboardService.java`, `dashboard/DashboardController.java`, test `dashboard/DashboardApiTests.java`
- Modify: `document/DocumentSummary.java` (`of` → `public`), `document/DocumentRepository.java`

**Interfaces:**
- Consumes: Task 2 `WarrantyService.views`, `findSavedWithItems`; `DocumentSummary`; `Clock`.
- Produces:
  - `record DashboardResponse(long savedBills, long savedBillsThisMonth, long products, long productsWithWarranty, BigDecimal totalSpent, BigDecimal spentThisMonth, Attention attention, WarrantyCounts warranties, List<WarrantyView> expiringSoon, List<MonthSpend> spendingByMonth, long billsWithoutDateOrTotal, List<ShopSpend> topShops, List<DocumentSummary> recentBills)` with nested `record Attention(long toReview, long readFailed, long reading)`, `record WarrantyCounts(long active, long expiringSoon, long expired, long noInfo)`, `record MonthSpend(String month /* "YYYY-MM" */, BigDecimal amount, long bills)`, `record ShopSpend(String name, BigDecimal amount, long bills)`. Exact JSON names as spec §4.2.
  - `DocumentRepository`: `long countByUserIdAndStatus(UUID userId, DocumentStatus status)`; `long countByUserIdAndReadQueuedAtIsNotNull(UUID userId)`; `long countByUserIdAndStatusAndReadErrorIsNotNullAndReadQueuedAtIsNull(UUID userId, DocumentStatus status)`; `List<Document> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId)` (add `@EntityGraph(attributePaths = "items")` if `DocumentSummary.of` needs items).
  - `DashboardService.summary(UUID userId): DashboardResponse` (`@Transactional(readOnly = true)`); `GET /api/dashboard`.
  - Amounts: `BigDecimal` sums with scale 2 (`setScale(2)`); zero = `0.00`.
  - Top shops: key = `sellerName.strip().toLowerCase(Locale.ROOT)`; display name = the spelling on the bill with the latest purchase date (ties: latest createdAt); bills without seller or total skipped; top 5 by amount desc, ties by name.

- [ ] **Step 1: Write failing tests** `DashboardApiTests extends DocumentApiTestBase` (`TODAY = 2026-10-07`):
```java
@Test void emptyAccountIsAllZeros()                 // 200; savedBills 0; totalSpent 0.00; spendingByMonth size 12 all zero; lists empty
@Test void countsOnlySavedBills()                   // 2 saved (totals 1000.00, 2500.50), 1 extracted (total 999) → savedBills 2, totalSpent 3500.50, attention.toReview 1
@Test void attentionCounts()                        // queued via DocumentService.createFromBytes → reading 1; drain read with FakeDetailExtractor.willFail → readFailed 1
@Test void spendingMonthsWindow()                   // 12 entries; first "2025-11", last "2026-10"; a bill dated 2026-09-15 total 38200 → entry "2026-09" amount 38200.00 bills 1
@Test void spendingOutsideWindowIsExcluded()        // purchase 2025-10-31 and 2026-11-01 → not in any month; both in totalSpent
@Test void billWithoutDateCountsInTotalNotChart()   // total 500, no date → totalSpent includes 500; billsWithoutDateOrTotal 1
@Test void thisMonthNumbers()                       // purchase 2026-10-02 → savedBillsThisMonth 1, spentThisMonth = its total
@Test void topShopsGroupedCaseInsensitive()         // "Croma " 100, "croma" 200, "Amazon" 250 → [Croma 300 (2 bills), Amazon 250]; display "croma" (latest purchase date) per rule
@Test void productsAndWarranties()                   // 3 items: one active, one expiring (2026-10-17), one no info → products 3, productsWithWarranty 2, warranties counts, expiringSoon[0].daysLeft 10
@Test void recentBillsAnyStatusNewestFirst()        // 6 uploads → 5 returned, newest first, includes an UPLOADED one
@Test void otherUsersDataAbsent()
```
- [ ] **Step 2: Run, expect FAIL.** `./mvnw test -Dtest=DashboardApiTests -Dmaven.compiler.useIncrementalCompilation=false`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the full suite, expect PASS.**
- [ ] **Step 5: Checkpoint.**

---

### Task 4: Frontend — URL filters on Documents, Warranties page, header

**Files:**
- Create: `lib/warranties.ts`, `components/warranties/WarrantyStatusPill.tsx`, `pages/WarrantiesPage.tsx`
- Modify: `pages/DocumentsPage.tsx`, `components/AppHeader.tsx`, `App.tsx`

**Interfaces:**
- Consumes: Task 2 JSON (`WarrantyList`, `WarrantyView`, `WarrantyStatus`).
- Produces:
  - `lib/warranties.ts`: types `WarrantyStatus`, `WarrantyView`, `WarrantyList`; `listWarranties(params: { status?: WarrantyStatus; q?: string }): Promise<WarrantyList>`; `WARRANTY_STATUS_LABELS` (`ACTIVE: 'Active'`, `EXPIRING_SOON: 'Expiring soon'`, `EXPIRED: 'Expired'`, `NO_INFO: 'No info'`).
  - `WarrantyStatusPill({ status, daysLeft }: { status: WarrantyStatus; daysLeft: number | null })` — ACTIVE emerald `ShieldCheck` "{n} days left"; EXPIRING_SOON amber `Clock` "{n} days left" ("Ends today" at 0, "1 day left"); EXPIRED rose `ShieldX` "Expired {n} days ago" ("Expired yesterday" at −1); NO_INFO slate `ShieldQuestion` "No end date". (Check lucide 1.x names with the typechecker.)
  - Documents URL filters: `useSearchParams` is the source of truth for `q`, `type`, `status`, `source`, `month` (replace the `useState`s); changing a filter writes the URL with `replace: true`; `month` (`YYYY-MM`) filters by `purchaseDate.startsWith(month)` in the browser and shows a chip "Sep 2026 ×" that clears it; the "filtered" empty message considers `month` too.
  - `/warranties` route inside `RequireAuth`; header links: **Dashboard** (`/home`), Documents, **Warranties**, Gmail.

- [ ] **Step 1:** `lib/warranties.ts` + `WarrantyStatusPill`.
- [ ] **Step 2:** Documents page URL filters + month chip.
- [ ] **Step 3:** Warranties page per spec §5: title + one line ("Every product on your saved bills and how long its warranty lasts."); tabs with counts synced to `?status=` (`All` = no param); search synced to `?q=` with a 300 ms debounce; table on `md+` (Product + model/serial under it · Shop · Ends · Status pill · "Open bill" link), cards below `md`; skeleton while loading; `Alert` + **Try again** on error; empty states: no saved bills → "No saved bills yet. Save a bill with its warranty details to see it here." + link to Documents; tab/search empty → "Nothing here." Stale responses ignored (request counter), like `GmailPage`.
- [ ] **Step 4:** Header + route.
- [ ] **Step 5: Verify.** `npm run typecheck`, `npm run build` pass. Checkpoint.

---

### Task 5: Frontend — the Dashboard

**Files:**
- Create: `lib/dashboard.ts`, `pages/DashboardPage.tsx`, `components/dashboard/{StatCard,WarrantyHealth,ExpiringList,SpendingChart,TopShops,RecentBills,GmailCard}.tsx`
- Delete: `pages/HomePage.tsx` (route `/home` → `DashboardPage`)
- Modify: `App.tsx`

**Interfaces:**
- Consumes: Task 3 JSON; Task 4 `WarrantyStatusPill`, `/warranties?status=`, Documents URL params; existing `getGmail()` (`lib/gmail.ts`), `SourceBadge`, `FileTypeIcon`, `StatusBadge`, `formatAmount`, `useAuth`.
- Produces: `lib/dashboard.ts` types mirroring `DashboardResponse` + `getDashboard(): Promise<Dashboard>`; link builders (in `lib/dashboard.ts`): `monthLink(month) = /documents?status=SAVED&month=${month}`, `shopLink(name) = /documents?status=SAVED&q=${encodeURIComponent(name)}`, `warrantiesLink(status?)`.

- [ ] **Step 1: `lib/dashboard.ts`.**
- [ ] **Step 2: Components** exactly as spec §5:
  - `StatCard({ icon, label, value, sub, to?, onClick?, tone? })` — whole card is a `Link` (or button for "Total spent", which scrolls to the chart via an element id); `tone="attention"` = amber border/icon when value > 0.
  - `WarrantyHealth` — one stacked bar, each segment a `Link` sized by count with `min-w` so 1-of-100 stays visible, `aria-label` "Active: 18"; legend items link too; all zero → "No warranty details yet".
  - `ExpiringList`, `TopShops` (rows with a proportional bar), `RecentBills`, `GmailCard` (loads `getGmail()` itself; not configured → "Gmail import isn't set up"; no accounts → **Connect Gmail**; else accounts count, `counts.toReview`, last scan relative time, **Review files**).
  - `SpendingChart` — 12 bars in a flex row, heights `% of max` (min 2 px when amount > 0), each bar a `Link` (`monthLink`) with `aria-label` "Sep 2026: 38,200.00 from 4 bills" and a tooltip shown on `hover` and `focus-visible` (pure CSS `group`); x labels short month, year shown on the first bar and on January; footnote "N saved bills have no date or total and aren't shown." when `billsWithoutDateOrTotal > 0`; all zero → empty message.
- [ ] **Step 3: `DashboardPage`.** Greeting by local hour (<12 morning, <17 afternoon, else evening) + first name; subtitle "Here's how your bills stand today."; buttons **Upload a bill** (`/documents`) and **Import from Gmail** (`/gmail`). Attention card links to `/documents?status=EXTRACTED` unless `toReview + readFailed + reading == 0` and Gmail has files to review → `/gmail`; its sub-line lists only non-zero parts ("2 to review · 1 failed · 7 in Gmail"). First run (savedBills 0 and recentBills empty) → welcome panel only. Skeleton while loading; `Alert` + **Try again** on error. Grid: cards `grid-cols-1 sm:grid-cols-2 lg:grid-cols-4`; sections `lg:grid-cols-2`.
- [ ] **Step 4: Verify.** `npm run typecheck`, `npm run build` pass; browser check (controller): desktop + 375 px, every link lands on the right filtered list.
- [ ] **Step 5: Checkpoint.**

---

### Task 6: Docs

- [ ] **Step 1:** `docs/task-4-dashboard.md` in the task-2/3 style: what happens where (diagram), the rules (§3), endpoints with JSON, why the backend computes, the charts (hand-made), URL filters, try-it, tests, known limits (no currency, saved bills only).
- [ ] **Step 2:** `docs/api-contract.md` (dashboard + warranties sections, top list, the type-mismatch 400), `CLAUDE.md` (Task 4 entry; `warranty` and `dashboard` packages; tests use `FixedClockConfig`, today 2026-10-07). Also correct `docs/task-2-documents.md` if it says save doesn't fill the end date.
- [ ] **Step 3: Final verification.** Backend full suite; frontend typecheck + build. Checkpoint.
