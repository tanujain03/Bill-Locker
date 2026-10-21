# CLAUDE.md

Guidance for AI-assisted work on Bill Locker (hackathon project).

## How the user works

The user is **rebuilding Bill Locker from scratch on branch `restart`** to understand
every line: each task adds one feature (frontend + backend), kept minimal and explained
("what happens where"). Keep code small and readable, with short comments that explain
*why*. Write a task guide in `docs/` (see `docs/task-1-auth.md`). Don't add features,
libraries or abstractions before the user asks for them. Never commit, merge or push
unless asked.

The earlier full build lives on branch `dev` (steps 1–7 committed). Its uncommitted
steps 8–9 (brand registration, Chrome extension) are in `git stash` as
"dev: steps 8-9 work (before restart)". It's for reference only. Don't copy it wholesale.

## Tasks done on `restart`

- Task 1: first page `/` = sign in (split screen `AuthLayout` + `PromoPanel`, layout modelled
  on a Salesforce-style login; no separate landing page), remember me (localStorage vs
  sessionStorage), `useScreenScale` (scales the auth screens to the window via the root
  font size; design size 1440×900; one screen high, never scrolls), sign up / sign out, forgot + reset password
  (one-time link, SHA-256 hash stored, 30 min; emailed over SMTP by `EmailResetLinkSender`
  when `MAIL_USERNAME` is set (Gmail + App Password), else logged by `LogResetLinkSender`;
  chosen in `ResetLinkSenderConfig`). Guide: `docs/task-1-auth.md`.
- Task 2: documents (`/documents`, `/documents/:id`): upload (PDF/JPEG/PNG/WebP by magic bytes,
  10 MB, bytes in `document_files`), read with Google Gemini free tier (`POST /{id}/extract`,
  synchronous, AI call outside the DB transaction; `DetailExtractor` interface,
  `GeminiDetailExtractor` via `RestClient` (`GEMINI_MODEL` may list backup models, tried on 503/429), `GeminiAnswerParser` forgives messy values), review
  form with the CSV fields (`invoice_warranty_fields.csv`: bill fields on `documents`, products
  on `document_items`), copy per field / product / all, save + edit (`PUT`), search + filters,
  download, delete. Status UPLOADED → EXTRACTED → SAVED. Guide: `docs/task-2-documents.md`.
- Task 3: Gmail import (`/gmail`): connect several Gmail addresses (read-only OAuth: one-time state + PKCE
  + an HttpOnly `gmail_connect` cookie so the callback only works in the browser that clicked Connect;
  refresh token AES-GCM encrypted with `GMAIL_TOKEN_KEY`), scan the newest 200 emails for PDF/image bills,
  three tabs (To review / Ignored / Imported), import / ignore / restore per file. The database is the
  queue: `GmailScanWorker`, `GmailImportWorker` and `DocumentReadWorker` run every 3 s from
  `WorkerSchedule`; imported documents are read by Gemini in the background (`documents.read_queued_at`,
  `read_error`, `source_gmail`; "Reading…" in the UI). Guide: `docs/task-3-gmail.md`.
- Task 4: dashboard (`/home`, `DashboardPage`) + warranties page (`/warranties`), both computed on the
  backend from **saved** bills' product lines (`GET /api/dashboard`, `GET /api/warranties`; packages
  `dashboard`, `warranty`; rules in `WarrantyRules`, "today" from a `Clock` bean). Hand-made charts (no
  library); every card/bar/row links to a filtered list; Documents filters live in the URL
  (`?q=&type=&status=&source=&month=`). Saving a bill now fills an empty warranty end date.
  Guide: `docs/task-4-dashboard.md`.
- Task 5: warranty registration links per product (`document_items.brand`, `registration_url`,
  `registration_source` = DOCUMENT / QR_CODE / WEB_SEARCH / SEARCH / USER). Reading: `QrCodeReader` (ZXing +
  PDFBox) decodes QR links and passes them to Gemini, which also returns `brand` + `warrantyRegistrationUrl`.
  The link shows as a clickable link (new tab), not an input. No link/QR: the page shows the detected brand and
  asks to confirm; only then `POST /api/documents/{id}/registration-page {position, brand}` (`RegistrationFinder`:
  Gemini + `google_search` tool, then `LinkChecker`: https + public addresses only, re-checked per redirect; else a
  Google search link) and the tab (opened during the click) goes there. Tests use `FakeRegistrationFinder`. Only links to brand pages; never submit their
  forms. Guide: `docs/task-5-warranty-registration.md`.
- Warranties vs bills: the Warranties page and the dashboard's warranty numbers only count products on an
  INVOICE or WARRANTY_CARD, or with warranty details (`WarrantyRules.hasWarranty`). `/bills` (`BillsPage`,
  "Bills & receipts" in the nav, "Bills" on the phone bar) lists documents of type RECEIPT, labelled
  "Bill / receipt" (rides, food, fuel, utility/phone bills; the Gemini prompt says so). No new BILL type: the
  local DB has a CHECK constraint on `documents.document_type`. `components/documents/DocumentList.tsx` is shared.
  Bills/receipts are light: after the AI read a RECEIPT goes straight to SAVED (`DocumentService.statusAfterRead`;
  invoices stay EXTRACTED for review), with `documents.category` (`BillCategory`: TRAVEL, FOOD, GROCERIES, FUEL,
  UTILITIES, PHONE_INTERNET, SHOPPING, HEALTH, OTHER, + since task 9 ELECTRONICS, APPLIANCES, FASHION, HOME,
  ENTERTAINMENT; plain varchar, parsed in Java, so no DB enum constraint; kept for every type since task 9). The form
  shows only type, category, shop, date, amount, number for a RECEIPT; `/bills` has category chips (`?category=`).
- UI/UX pass: icon-rail sidebar (hover opens, pin button; navigation + Settings only), top bar with search
  (`Ctrl K` → `/documents?q=`), 🔔 (`useAttention`), one-click `UploadButton` + `DropAnywhere`, phone tab bar,
  `/settings` (`PUT /api/users/me`, `PUT /api/users/me/password`; wrong current password = 400 `WRONG_PASSWORD`,
  never 401, because any 401 for a sent token signs the browser out as "session expired"), ₹ amounts,
  `usePageTitle`. Checklist: `docs/ui-ux-changes.md`; reasons: `docs/ui-ux-review.md`.
- Task 6: "Ask to your locker" chat assistant (`components/assistant/AssistantChat.tsx` in `AppLayout`, history only in
  React state; `POST /api/assistant/chat`, package `assistant`). Gemini plans (QUERY with one SELECT, or REPLY: it
  never changes data), `ReadOnlyQueryRunner` runs the SQL read-only on its **own connection logged in as `bill_locker_ai`**
  (password `AI_DATABASE_PASSWORD`; SELECT only on six `security_barrier` `ai_*` views; member of no role; new connection per
  question, closed after; one question at a time), the views filter on `app.user_id` + `app.user_sig` (sha256 of secret + id,
  secret in table `ai_secret`); Java query timeout 3 s, 50 rows, values cut to 500 chars; one retry on a failed query, then Gemini
  answers from the rows. `AssistantDatabaseSetup` creates the login, secret and views at startup (Java strings, no migrations).
  No superuser needed (task 8, so it works on Neon). Tests use `FakeAssistantModel`. Guide: `docs/task-6-assistant.md`.
- Task 7: live. Frontend on Vercel (`frontend/vercel.json`: `/api/*` rewritten to `https://bill-locker-backend.onrender.com`,
  everything else → `index.html`; deploys from `main`), backend on Render (Docker, `backend/Dockerfile`, root `backend`,
  region Ohio; env vars in Render, never in git; free plan sleeps after 15 min), database on Neon (project Bill-Locker,
  branch `production`, us-east-2, direct host without `-pooler`). Local dev keeps the local DB via `backend/.env`.
  `server.port=${PORT:8080}`, `server.forward-headers-strategy=framework`. Guide: `docs/task-7-deploy.md`.
- Task 8: chat assistant without superuser rights (Neon): see Task 6 above. Plan and reasons:
  `docs/task-8-assistant-on-neon-plan.md`.
- Task 9: expense tracker (`/expenses`, `ExpensesPage`, package `expense`): `GET /api/expenses?from=&to=` adds up a period
  of **saved** bills (total, the period before, daily average, pace, per category / product line / shop / day, months).
  Period buttons This month / Last month / Last 6 months / Last year / Custom range (`presetRange` in `lib/expenses.ts`;
  URL `?period=…&from=&to=`). Monthly budget in `users.monthly_budget` (`PUT /api/expenses/budget`), scaled by months for
  longer periods. Every document type keeps a `category` now (Gemini picks it); bills without one show as "Not sorted yet".
  Documents page filters `?from=&to=` and `?category=` (`NONE` = no category). Dashboard "Total spent" → `/expenses`. The
  chat assistant's `ai_me` has `monthly_budget` and its prompt has the same spending/period rules.
  Level 2: period dropdown `PeriodMenu` (This month … Last 12 months, This year, Custom range; ‹ › via `shiftRange`),
  category limits (table `category_budgets`, `PUT /api/expenses/budgets/{category}`, view `ai_category_budgets`),
  recurring payments (same shop in ≥ 2 months, amounts within ±25% of the median, last 6 months), spending per weekday,
  Export CSV (client-side from the Documents list, formula-safe).
  Guide: `docs/task-9-expenses.md`.
- Task 10: warranty claim kit (`/warranties/claim/:documentId/:position`, `ClaimKitPage`; "Claim" on Warranties rows,
  "Claim kit" on saved products). `GET /api/documents/{id}/items/{position}/claim-kit` (`ClaimKitService`, uses
  `WarrantyRules`): facts, status, brand support + Google Maps service-centre search links, missing facts. The claim email
  is written in the browser (`lib/claims.ts` `claimEmail`) and opened with `mailto:` — the app never sends anything.
  `WarrantyView.position`. Print sheet: `print:hidden` on the app chrome. Guide: `docs/task-10-claim-kit.md`.
- Task 11: sample data ("Try with sample data" on the empty dashboard, Settings → Sample data, violet dashboard banner).
  Package `sample`: `SampleBills` (~30 bills dated from today: every warranty state + 3 months of receipts, monthly ones
  recurring), `SamplePdf` (PDFBox one-pager "SAMPLE BILL"), `SampleDataService` (one transaction; again → 409
  `SAMPLE_DATA_EXISTS`). `documents.sample` (nullable Boolean) + `sample` on DocumentSummary/Detail → "Sample" badge;
  `DELETE /api/sample-data` removes only flagged bills. Frontend `lib/useSampleData.ts`. Guide: `docs/task-11-sample-data.md`.
- Task 12: duplicate bills. Same file: `documents.content_hash` (SHA-256 hex); upload → 409 `DUPLICATE_FILE` with
  `documentId` (new optional field on `ApiErrorBody`/`ApiException`), the browser opens that bill; a Gmail import of the same
  bytes links to the existing document. Same bill, other file: `DuplicateRules` (same purchase date + same normalised number
  and no different shop, or same shop + same total) → `DocumentDetail.possibleDuplicates`, `DocumentSummary.possibleDuplicate`
  ("Possible copy" badge, `DuplicateWarning.tsx`); "Keep both" = `POST /api/documents/{id}/not-duplicate`
  (`documents.not_duplicate`). Test `pdfBytes()` is unique per call. Guide: `docs/task-12-duplicate-bills.md`.
- Task 13: product pages (`/products/:documentId/:position`, `ProductPage`; linked from Warranties names, dashboard
  "Expiring soon", "Product page" on saved bill products (`DetailsForm.savedLinks`), the claim kit title). Package `product`:
  `GET /api/documents/{id}/items/{position}/product` (`ProductService`: `WarrantyRules`, owned days, cost per month = price ÷
  whole months owned (min 1), related documents via `DocumentRepository.findMentioning` by serial, else model number, missing
  facts). Guide: `docs/task-13-product-pages.md`.
- Task 14: return windows. `documents.return_by_date` (`DocumentDetails.returnByDate`, 14th component + a constructor
  without it); save rejects a date before the purchase (`checkDates`); Gemini `returnWindowDays` (only if printed) →
  purchase + days (`GeminiAnswerParser.returnBy`); dashboard `openReturns`. Frontend `lib/returns.ts` (closing = ≤ 3 days),
  form field with +7/+10/+15/+30, `ReturnBadge`/`ReturnBanner`, dashboard strip, 🔔. View `ai_documents.return_by_date`.
  Sample air fryer: bought 5 days ago, 10-day window. Guide: `docs/task-14-return-windows.md`.

- Task 15: service and maintenance records (`servicerecord`, `/api/services`, `/services`). Repairs,
  routine maintenance, parts and claims belong to a SAVED bill's permanent product ID; the document/
  position endpoints remain, with an optional productId guard. Existing owned documents can be
  linked as attachments. `ServiceRules` uses the Clock for overdue / due in 30 days / upcoming;
  repeatMonths computes the next date from the service date. The newest scheduled record per
  product drives reminders. Product pages include history/costs, claim kits include previous
  services, dashboard/bell show services due. `ai_service_records` uses the existing signed user
  filter and SELECT-only AI view permissions. Four service records accompany sample bills;
  document/product deletion cascades to records. The new table is `product_service_records`, keeping
  the old build's incompatible `service_records` table and its rows untouched. Guide: `docs/task-15-service-records.md`.


- Task 16: split bills with friends (`billsplit`, `/api/splits`, `/splits`). Owner-managed groups,
  guest placeholders and seven-day, one-time personal invite links (`/split-invite#token=…`;
  SHA-256 hash stored). Equal/custom INR shares use exact paise. Immutable expense snapshots can
  start from an owned SAVED bill; original files/buyer details stay private and document deletion
  leaves the ledger intact. Manual payments remain pending until recipient confirmation (owner
  may confirm for an unclaimed guest). Request UUIDs and group locks prevent duplicate expenses,
  over-settlement and invite races. No AI access, payments integration or automatic messaging.
  Owner-only group deletion removes all shared history for everyone after UI confirmation, keeping
  original bills. Unique `bill_split_*` tables preserve legacy data. Guide: `docs/task-16-bill-splitting.md`.

- Task 17: price comparison (`pricecompare`, `/api/price-comparison`, `/compare-prices`). SerpApi
  Google Shopping for India, one explicit request per search, no new dependencies. Backend-only
  `SERPAPI_API_KEY`; per-user 30-minute cache and persisted shared rolling-31-day budget (default
  200, max 250), 40/hour, five new attempts/user/day. Failures count locally. Quota reservation
  uses a PostgreSQL transaction lock. Model-title matches are labelled, not verified variants;
  INR listings, conditional/sponsored labels and safe retailer/Google links. No background tracking,
  automatic paid upgrades or AI access. Tables `price_compare_attempts` / `price_compare_cache`.
  Cards open retailer product pages; Google-only results use an explicit, quota-counted SerpApi
  detail lookup, scoped to the caller’s cached offer and cached afterward. No eager detail calls.
  Local title relevance filters unrelated offers, classifies matches/alternatives, checks requested
  SPF/quantity/model, and preserves provider order by default (`ProductRelevance`).
  Guide: `docs/task-17-price-comparison.md`.

## Repository

- `frontend/` — React 19 + TypeScript 5.9 + Vite 6 + Tailwind CSS 4 + React Router 7 +
  lucide-react 1.x. Nothing else on purpose: plain `fetch` in `src/lib/api.ts` (the only
  place that calls the backend), `useState` forms, no mock API. Vite proxies `/api` to
  `http://localhost:8080`. `src/lib/auth-context.ts` + `components/AuthProvider.tsx` hold
  the signed-in user; `components/RouteGuards.tsx` (`RequireAuth`, `GuestOnly`).
- `backend/` — Spring Boot 4.1.1, Java 21, Maven wrapper, package `project.bill_locker`.
  Packages: `security` (SecurityConfig, JWT), `user` (incl. Settings endpoints), `auth` (incl. password reset),
  `common` (BaseEntity, ApiException, GlobalExceptionHandler), `document` (+ `document.ai`,
  `document.registration`), `gmail`, `dashboard`, `warranty`, `assistant`, `expense`, `sample`, `product`, `servicerecord`, `billsplit`.
  Tables: `users`, `password_reset_tokens`, `documents`, `document_items`, `document_files`,
  `gmail_accounts`, `gmail_emails`, `gmail_files`, `gmail_connect_states`, `category_budgets`, `product_service_records`, and `bill_split_*`.
- `docs/api-contract.md` — the full target REST API; its top lists what is built so far.
  `docs/database-design.md` — the target design (13 tables); entity classes at tag
  `step-2-database`. Hibernate `ddl-auto=update` creates tables. No migration scripts or
  Flyway: the user explicitly rejected that style.
- Root `pom.xml` — aggregator only (`<module>backend</module>`), so IntelliJ IDEA
  (Community 2025.1, JDK named "21") opens the repo root with `backend` as a Maven
  module. Build from `backend/`. `.run/Backend.run.xml` is the shared run configuration.
  The user edits the frontend in WebStorm. Never move/rename a folder the user has open
  in IntelliJ without telling them first.
- Work only in this checkout. A different copy exists at `D:\projects\okruti\repo\Bill-Locker`;
  don't use it. Its Docker stack `bill-locker` uses ports 5433/9000/9001 — never touch it.
  Port 5432 is the user's local Windows PostgreSQL 17, database `BillLocker`, user
  `postgres`; psql is at `C:\Program Files\PostgreSQL\17\bin`. The DB still holds the
  old build's tables; `users` keeps the same columns, so old accounts still sign in.

## Backend commands (run inside `backend/`)

- `.\mvnw.cmd spring-boot:run` (Git Bash: `./mvnw`) or IntelliJ's **Backend** run config.
  Reads `backend/.env` (git-ignored; keys in `backend/.env.example`: `DATABASE_URL`,
  `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `FRONTEND_URL`, `MAIL_HOST`, `MAIL_PORT`,
  `MAIL_USERNAME`, `MAIL_PASSWORD`, `GEMINI_API_KEY`, optional `GEMINI_MODEL`, `GOOGLE_CLIENT_ID`,
  `GOOGLE_CLIENT_SECRET`, `GMAIL_TOKEN_KEY`, optional `GMAIL_REDIRECT_URI`; Gmail import is off without the first three;
  `AI_DATABASE_PASSWORD`, chat assistant off without it). Never put real
  values in `application.properties` or commit them.
- `.\mvnw.cmd test` — MockMvc API tests against Testcontainers (`pgvector/pgvector:pg17`,
  needs Docker); never touches the local database. Keep all tests green. Tests swap
  `ResetLinkSender` for `RecordingResetLinkSender` to read reset links. They swap
  `DetailExtractor` for `FakeDetailExtractor` and `GoogleApi` for `FakeGoogleApi`: Gemini and Google are
  never called in tests. Tests run with `app.workers.enabled=false` and call the workers' `runOnce()`.
  `FixedClockConfig` pins "today" to 2026-10-07 (`FixedClockConfig.TODAY`).
- IntelliJ's run uses devtools: recompiling (e.g. `mvnw compile`/`test`) restarts a
  running app. While it runs, build with `-Dmaven.compiler.useIncrementalCompilation=false`
  (Maven's default deletes all classes first, and devtools restarts in the gap).
  After a `pom.xml` change the user must click "Load Maven Changes" and re-run — tell them.

## Backend conventions

- Controller (HTTP ↔ Java) → service (rules, `@Transactional`) → repository (Spring
  Data). Request/response shapes are records; entities never go to the browser.
- Errors: throw `ApiException(status, CODE, message[, fieldErrors])`;
  `GlobalExceptionHandler` renders `{ success:false, code, message, fieldErrors? }`.
  401s come from `JsonAuthenticationEntryPoint`. Public endpoints are listed in
  `SecurityConfig.PUBLIC_ENDPOINTS`.
- The user id comes only from the token: `@AuthenticationPrincipal Jwt jwt` +
  `CurrentUser.id(jwt)`. Load user data with `findByIdAndUserId`, so another user's
  item is a 404.
- Entities extend `BaseEntity` (UUID id, created_at, updated_at via callbacks);
  `@Enumerated(STRING)`, `@OnDelete`, constraint names `fk_/uk_/idx_`. `update` never
  drops/renames/retypes columns: when a table's shape changes incompatibly, reset the
  dev DB (with the user's OK).
- Validation: annotations on request records; `@ValidPassword` holds the password rules.
- Don't reveal whether an email has an account (login and forgot-password give the same answer).

## Frontend commands (run inside `frontend/`)

- `npm run dev` (:5173), `npm run typecheck`, `npm run build`. No tests or lint yet.
- Node here is 20.18 — keep Vite 6 / TypeScript 5.9 (Vite 7+ needs Node ≥ 20.19).
- After `npm install` changes packages, restart a running `npm run dev` (stale dep cache).

## Frontend conventions

- Pages show backend `fieldErrors` under inputs (`FormParts.tsx` `TextField`) and the
  `message` in an `Alert`. Forms use `noValidate`: the backend is the source of truth.
- Colours for status always come with an icon + label. Never render API text as HTML.
- Buttons: `components/Button.tsx` (`<Button variant icon busy>`; `buttonClass()` for a `<Link>` that looks like
  one); one `primary` per screen. Never `window.confirm`: `const { confirm, toast } = useFeedback()`
  (`lib/feedback-context.ts`, shown by `FeedbackProvider`); `await confirm({ title, message, confirmLabel, danger })`.
- lucide-react 1.x renamed icons; the typechecker catches wrong names.

## Roadmap (from the README, one task at a time when the user asks)

Documents upload + reading bills (done, task 2) → dashboard + warranties (done, task 4; separate product
pages later) → services, reminders, search → AI assistant (Gmail import done, task 3). Also later: send reset emails in the
background, login rate limiting, Swagger, Docker Compose.
Never build anything that submits brand forms for the user or gets around CAPTCHAs.
