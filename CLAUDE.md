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

## Repository

- `frontend/` — React 19 + TypeScript 5.9 + Vite 6 + Tailwind CSS 4 + React Router 7 +
  lucide-react 1.x. Nothing else on purpose: plain `fetch` in `src/lib/api.ts` (the only
  place that calls the backend), `useState` forms, no mock API. Vite proxies `/api` to
  `http://localhost:8080`. `src/lib/auth-context.ts` + `components/AuthProvider.tsx` hold
  the signed-in user; `components/RouteGuards.tsx` (`RequireAuth`, `GuestOnly`).
- `backend/` — Spring Boot 4.1.1, Java 21, Maven wrapper, package `project.bill_locker`.
  Packages: `security` (SecurityConfig, JWT), `user`, `auth` (incl. password reset),
  `common` (BaseEntity, ApiException, GlobalExceptionHandler), `document` (+ `document.ai`), `gmail`.
  Tables: `users`, `password_reset_tokens`, `documents`, `document_items`, `document_files`,
  `gmail_accounts`, `gmail_emails`, `gmail_files`, `gmail_connect_states`.
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
  `GOOGLE_CLIENT_SECRET`, `GMAIL_TOKEN_KEY`, optional `GMAIL_REDIRECT_URI`; Gmail import is off without the first three). Never put real
  values in `application.properties` or commit them.
- `.\mvnw.cmd test` — MockMvc API tests against Testcontainers (`pgvector/pgvector:pg17`,
  needs Docker); never touches the local database. Keep all tests green. Tests swap
  `ResetLinkSender` for `RecordingResetLinkSender` to read reset links. They swap
  `DetailExtractor` for `FakeDetailExtractor` and `GoogleApi` for `FakeGoogleApi`: Gemini and Google are
  never called in tests. Tests run with `app.workers.enabled=false` and call the workers' `runOnce()`.
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
- lucide-react 1.x renamed icons; the typechecker catches wrong names.

## Roadmap (from the README, one task at a time when the user asks)

Documents upload + reading bills (done, task 2) → products + warranties + dashboard →
services, reminders, search → AI assistant (Gmail import done, task 3). Also later: send reset emails in the
background, login rate limiting, Swagger, Docker Compose.
Never build anything that submits brand forms for the user or gets around CAPTCHAs.
