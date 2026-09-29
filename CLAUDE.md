# CLAUDE.md

Guidance for AI-assisted work on Bill Locker (hackathon project; built in phases).

## Repository

- `frontend/` — React 19 + TypeScript + Vite 6 + Tailwind CSS 4 + TanStack Query 5 +
  React Router 7 + React Hook Form + Zod 3 + Recharts 3 + lucide-react 1.x. Complete.
- `docs/api-contract.md` — **source of truth** for the REST API. Frontend types in
  `frontend/src/types/` mirror it; the mock API in `frontend/src/mocks/` implements it.
- `backend/` — Spring Boot 4.1.1, Java 21, Maven wrapper, package `project.bill_locker`.
  Step 2 (database design) is done as **JPA entity classes**: Hibernate
  `ddl-auto=update` creates the tables in the user's local PostgreSQL. No migration
  scripts, no Flyway — the user explicitly rejected that style.
- `docs/database-design.md` — entities, columns, rules, delete behaviour, local setup,
  pgvector install, `ddl-auto=update` caveats.
- Root `pom.xml` — aggregator only (`<module>backend</module>`), so IntelliJ IDEA
  (Community 2025.1, JDK named "21") opens the repo root with `backend` as a Maven
  module. Not a parent: build from `backend/`. `.run/Backend.run.xml` is the shared run
  configuration (working dir `backend/`); `.idea/` is git-ignored and regenerated. The
  user edits the frontend in WebStorm (IntelliJ Community has no JS/TS support).
  Never move/rename a folder the user has open in IntelliJ without telling them first.
- Work only in this checkout. A different copy of the project exists on this machine
  (`D:\projects\okruti\repo\Bill-Locker`); the user said not to use it. Its Docker stack
  is named `bill-locker` and uses ports 5433/9000/9001 — never touch it. Port 5432 is
  the user's local Windows PostgreSQL 17 (pgvector not installed yet, psql not on PATH).

## Backend commands (run inside `backend/`)

- `.\mvnw.cmd spring-boot:run` (Git Bash: `./mvnw`) — uses `backend/.env` (copy of
  `.env.example`, git-ignored; found when started from `backend/` or the repo root) or
  the env vars `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD`. Never commit
  a password.
- `.\mvnw.cmd test` — Testcontainers (`pgvector/pgvector:pg17`, needs Docker); never
  touches the local database. Keep all tests green.

## Database conventions

- The entity classes are the schema. When you change one, update
  `docs/database-design.md`. `update` never drops/renames/retypes columns or updates
  enum CHECK constraints (see the doc, §6).
- Extend `AuditableEntity` (or `BaseEntity` for append-only rows): UUID ids,
  `created_at`/`updated_at` via callbacks. `@Enumerated(STRING)`; money `BigDecimal`
  `NUMERIC(12,2)`; `LocalDate` for dates, `Instant` (UTC) for instants; JSONB via
  `@JdbcTypeCode(SqlTypes.JSON)` on records/lists/maps.
- Name constraints `fk_<table>_<column>`, `uk_…`, `idx_…`. Declare delete behaviour with
  `@OnDelete`: user delete cascades everything; deleting a product keeps its documents
  (SET NULL + `Product.@PreRemove`) — never lose a bill.
- Warranty expiry is only derived (`WarrantyDates`, `Warranty` setters/callbacks).
  Status is never stored: `warranty.statusOn(user.today(clock))`.
- Every query on user data filters by the JWT user; `DocumentChunk.user` exists so RAG
  search filters without joins (`cosine_distance` in HQL).
- `schema.sql` must stay a single statement (`spring.sql.init.separator`): a plain
  `CREATE EXTENSION vector` without pgvector installed kills the Hikari connection
  (SQLSTATE 0A000) and aborts startup.
- Reminder dedupe keys use the formats listed in `docs/database-design.md`.

## Commands (run inside `frontend/`)

- `npm run dev` — dev server :5173, mock API on (`.env.development`)
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

## Backend phase checklist (from the spec)

Spring Security + JWT, PostgreSQL + pgvector (entities done in step 2), MinIO storage,
pluggable OCR (Document AI/Vision → Tesseract fallback), provider-agnostic AI
(`AIService`, `DocumentExtractionService`, `EmbeddingService`, `RagService`; Gemini
first), prompts in `ai/prompts/`, scheduled reminder job, Gmail OAuth (read-only scope,
encrypted refresh tokens, callback redirects to `/gmail?status=connected|error`),
Swagger, global exception handler returning `{ success:false, code, message, fieldErrors? }`,
tests (auth, authorization/isolation incl. RAG chunks, warranty maths, uploads, AI parsing),
Docker Compose (frontend, backend, postgres+pgvector, minio). Seed the demo account
`demo@billlocker.app` / `Demo@1234` with the same demo products as `src/mocks/seed.ts`.
