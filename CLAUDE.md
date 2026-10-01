# CLAUDE.md

Guidance for AI-assisted work on Bill Locker (hackathon project; built in phases).

## How the user works

The user builds Bill Locker **step by step to learn**: each step adds one feature to
the backend, kept simple and explained ("what happens where"). Keep code minimal and
readable, with short comments that explain *why*. Write or update a step guide in
`docs/` (see `docs/step-3-backend-basics.md`), and don't add features before the user
asks for them. Branches: only `dev` (where work happens) and `main` (merged into from
`dev` by pull request); steps 1-3 are in both, step 4 is on `dev`. Tag `step-2-database`
marks the full entity design. Never commit, merge to `main` or push unless asked.

## Repository

- `frontend/` — React 19 + TypeScript + Vite 6 + Tailwind CSS 4 + TanStack Query 5 +
  React Router 7 + React Hook Form + Zod 3 + Recharts 3 + lucide-react 1.x. Complete;
  runs against the mock API or the real backend (see Frontend ↔ backend below).
- `docs/api-contract.md` — **source of truth** for the REST API (its top lists what the
  real backend implements so far). Frontend types in `frontend/src/types/` mirror it;
  the mock API in `frontend/src/mocks/` implements all of it.
- `backend/` — Spring Boot 4.1.1, Java 21, Maven wrapper, package `project.bill_locker`.
  Step 3 = register/login (Spring Security + JWT) and document upload with the file
  bytes stored in PostgreSQL. Tables: `users`, `documents`, `document_files`.
  Step 4 = a background reader (`processing` package): PDFBox for PDF text, Tesseract
  OCR (Tess4J) for photos and scanned PDFs, rule-based `DetailExtractor` (no AI yet);
  documents end PROCESSED with `extractedText` + `extraction` (jsonb), or FAILED.
  Step 5 = `categories` (seeded by `DefaultCategories`), `products`, `warranties` (one
  per product, `@OneToOne`; dates in `WarrantyDates`, status computed, never stored),
  "Confirm & Save" (`POST /api/documents/{id}/confirm` → CONFIRMED + product), the
  warranties list and the dashboard summary.
  Packages: `security`, `auth`, `user`, `document`, `processing`, `product`, `warranty`,
  `dashboard`, `common`. Guides: `docs/step-3-backend-basics.md`,
  `docs/step-4-reading-documents.md`, `docs/step-5-products-and-warranties.md`.
- `docs/database-design.md` — the **target** design (13 tables). Its entity classes
  and tests are at tag `step-2-database`; bring parts back one feature at a time.
  Hibernate `ddl-auto=update` creates the tables. No migration scripts or Flyway: the
  user explicitly rejected that style.
- Root `pom.xml` — aggregator only (`<module>backend</module>`), so IntelliJ IDEA
  (Community 2025.1, JDK named "21") opens the repo root with `backend` as a Maven
  module. Not a parent: build from `backend/`. `.run/Backend.run.xml` is the shared run
  configuration (working dir `backend/`); `.idea/` is git-ignored and regenerated. The
  user edits the frontend in WebStorm (IntelliJ Community has no JS/TS support).
  Never move/rename a folder the user has open in IntelliJ without telling them first.
- Work only in this checkout. A different copy of the project exists on this machine
  (`D:\projects\okruti\repo\Bill-Locker`); the user said not to use it. Its Docker stack
  is named `bill-locker` and uses ports 5433/9000/9001 — never touch it. Port 5432 is
  the user's local Windows PostgreSQL 17, database `BillLocker` (capital letters), user
  `postgres`; pgvector not installed; psql is at `C:\Program Files\PostgreSQL\17\bin`.

## Backend commands (run inside `backend/`)

- `.\mvnw.cmd spring-boot:run` (Git Bash: `./mvnw`) or IntelliJ's **Backend** run
  configuration. Reads `backend/.env` (git-ignored; keys in `.env.example`:
  `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`). Never put
  real values in `application.properties` or commit them.
- `.\mvnw.cmd test` — MockMvc API tests against Testcontainers (`pgvector/pgvector:pg17`,
  needs Docker); never touches the local database. Keep all tests green.
- IntelliJ's run uses devtools: recompiling (e.g. `mvnw compile`) restarts a running app;
  after a `pom.xml` change that restart fails until the user clicks "Load Maven Changes"
  and re-runs — tell them.

## Backend conventions

- Controller (HTTP ↔ Java) → service (rules, `@Transactional`) → repository (Spring
  Data). Request/response shapes are records; entities never go to the browser.
- Errors: throw `ApiException(status, CODE, message[, fieldErrors])`;
  `GlobalExceptionHandler` renders `{ success:false, code, message, fieldErrors? }`
  with the codes from the contract. 401s come from `JsonAuthenticationEntryPoint`.
- The user id comes only from the token: `@AuthenticationPrincipal Jwt jwt` +
  `CurrentUser.id(jwt)`. Load user data with `findByIdAndUserId`, so another user's
  item is a 404.
- Entities extend `AuditableEntity` (or `BaseEntity`): UUID ids, timestamps via
  callbacks, `@Enumerated(STRING)`, delete rules with `@OnDelete`, constraint names
  `fk_/uk_/idx_`. `update` never drops/renames/retypes columns or updates enum CHECK
  constraints: when a table's shape changes incompatibly, reset the dev DB (with the
  user's OK).
- Uploads: magic-byte type check (`AllowedFileType`), cleaned file names, 10 MB limit
  (`spring.servlet.multipart.*`).
- Reading: `DocumentProcessor` (`@Scheduled` every 2 s, `app.processing.enabled`) uses
  short `DocumentService` transactions, never a transaction around OCR. `ApiTest` turns
  the schedule off; tests call `processPendingDocuments()` themselves.
- `DetailExtractor`: a line starting "Label : value" (Product, Brand, Price, Seller) wins
  over the fallbacks (known-brand list, brand-line guess, totals). When a real bill reads
  badly, add its OCR text (from `documents.extracted_text`) as a `DetailExtractorTests`
  case; existing documents pick up rule changes via **Read again** (`…/reprocess`).
- **Keep Tess4J at 5.19.0** unless a newer release's Windows DLL is built with linker
  ≤ 14.39: 5.20.0 (linker 14.51) needs msvcp140 ≥ 14.40, but the JDK's own msvcp140
  (14.36) is loaded first and OCR crashes the JVM ("Invalid memory access").

## Frontend ↔ backend

- `frontend/.env.development.local` (git-ignored) sets `VITE_API_MOCKING=false`, so
  `npm run dev` proxies `/api` to `http://localhost:8080`. `true` means the mock demo.
- `frontend/src/lib/features.ts`: `BACKEND_FEATURES` lists what the real backend has
  (now `documentProcessing`, `products`, `warranties`, `dashboard`). Unbuilt features
  are hidden: nav, top bar, routes redirect to `homePath()`, and parts of pages
  (`isFeatureEnabled('gmail' | 'services' | 'assistant' | 'search')`; hooks like
  `useGmailConnection(enabled)` / `useServiceRecords(id, enabled)` skip the request).
  Add a feature there when its endpoints land. Tests force mock mode
  (`vite.config.ts` `test.env`).

## Commands (run inside `frontend/`)

- `npm run dev` — dev server :5173 (mock or real backend, see above)
- `npm test` — Vitest (jsdom + MSW); `npm run lint`; `npm run typecheck`; `npm run build`
- Node here is 20.18 — keep Vite 6 / Vitest 3 / TypeScript 5.9 (Vite 7+ needs Node ≥ 20.19).

## Conventions

- Changing an endpoint or payload? Update `docs/api-contract.md`, `src/types/`, the
  service in `src/services/`, and the mock handler in `src/mocks/handlers.ts` together.
- Data fetching only through hooks in `src/hooks/` (query keys in `src/lib/query-keys.ts`);
  writes call `invalidateLockerData()` so dashboard/lists stay consistent.
- Every API-backed view handles loading, error (with retry), empty and success states.
- Reuse `src/components/ui/*` (Button, Card, Field/Input/Select, Modal, Badge, FilterTabs,
  EmptyState/ErrorState, PageHeader, StatCard). Status colours are reserved for status
  and always paired with an icon + label.
- Never render API text as HTML (`RichText` is the only formatter). No `dangerouslySetInnerHTML`.
- AI results are suggestions: nothing becomes product/warranty data without the
  review screen's "Confirm & Save". Missing values are `null` → "Not found".
- Warranty expiry = start + months − 1 day, computed by code (`utils/date.ts` preview,
  backend authoritative). Expiring soon = ≤ 30 days.
- lucide-react 1.x renamed icons (e.g. `Trash` not `Trash2`, `CircleQuestionMark`,
  `ShieldQuestionMark`); the typechecker catches wrong names.
- Files exporting React components must export only components (react-refresh lint);
  put shared constants/helpers in sibling `.ts` files.

## Backend roadmap (from the spec; done: auth + JWT, uploads, error handler, OCR + rules, products + warranties + dashboard)

Next candidates (no AI needed): **reminders/notifications** (daily `@Scheduled` job) and
**service records**; then **AI extraction** (better product names, category suggestion;
rules stay as a check). Later: MinIO storage, provider-agnostic AI (`AIService`,
`DocumentExtractionService`, `EmbeddingService`, `RagService`; Gemini first; prompts in
`ai/prompts/`), pgvector RAG, scheduled reminders, Gmail OAuth (read-only scope,
encrypted refresh tokens, callback redirects to `/gmail?status=connected|error`),
login rate limiting, Swagger, Docker Compose. Seed the demo account
`demo@billlocker.app` / `Demo@1234` with the demo products from `src/mocks/seed.ts`.
