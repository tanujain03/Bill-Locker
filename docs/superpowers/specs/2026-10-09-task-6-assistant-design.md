# Task 6 — Bill assistant chatbot (design)

Date: 2026-10-09 · Branch: `restart` · Builds on task 2 (documents), task 3 (Gmail import), task 4 (dashboard).

## 1. Goal

A chat assistant on every signed-in page answers questions about the signed-in user's own
data ("How much did I spend this month?", "Show my watch bill", "Which warranties expire
soon?"). No training and no extra data: Gemini writes a SQL `SELECT`, the backend runs it
**read-only** and **only over the user's own rows**, and Gemini turns the rows into a short answer.
The assistant can only look things up. Delete, upload or edit requests are answered with
"I can only look things up" plus the page to use.

**Done when:** the floating chat answers spending, bill, product, warranty and Gmail questions
from the database; a user never sees another user's data or any secret (password hash, tokens);
no SQL from Gemini can change data; it works at 375 px; all backend tests pass.

## 2. Decisions (agreed with the user)

| Topic | Decision |
|---|---|
| Data access | Gemini writes SQL (text-to-SQL), run against user-filtered views by a restricted DB role in a read-only transaction |
| What the bot sees | All of the user's own data (incl. buyer/seller contacts, Gmail emails/files); never secrets |
| Write requests | Refused in words (prompt) and impossible in practice (read-only transaction + role) |
| UI | Floating chat button + panel on every signed-in page |
| History | Only while the page is open (React state in `AppLayout`); last 10 messages go to the backend; no table |
| Web scraping | Not used: the data is already in our database. Web search for outside questions is possible later |
| Libraries | None new (backend reuses `RestClient` + Jackson + `JdbcTemplate`; frontend plain React) |

Out of scope: saved chat history, streaming answers, charts in answers, web search, actions
(upload/delete/edit), answering from file contents that were never extracted.

## 3. Database side

### 3.1 Views (created at startup)

Each view shows only the signed-in user's rows:
`WHERE user_id = current_setting('app.user_id', true)::uuid`. If the setting is missing the
filter is `NULL` and the view returns nothing, never everyone's rows.

| View | From | Columns |
|---|---|---|
| `ai_me` | `users` | `name`, `email`, `created_at` |
| `ai_documents` | `documents` | every column except `user_id` (id, file name/type/size, status, type, number, seller name/address/contact, buyer name/address/email, purchase date, tax, total, category, read error, source Gmail, created/updated) |
| `ai_items` | `document_items` joined to `documents` for the filter | every column (document_id, position, product, model, serial, unit price, warranty months/start/end/provider, brand, registration URL/source) |
| `ai_gmail_accounts` | `gmail_accounts` | `id`, `email`, `scan_status`, `scan_since`, `last_scanned_at`, `last_error`, `created_at` |
| `ai_gmail_emails` | `gmail_emails` | `id`, `account_id`, from name/email, subject, snippet, `received_at`, `kind` |
| `ai_gmail_files` | `gmail_files` joined to `gmail_emails` for the filter | `id`, `email_id`, file name, content type, size, `status`, `document_id`, `error` |

Never visible: `users.password_hash`, `password_reset_tokens`, `gmail_accounts.refresh_token_ciphertext`,
`gmail_connect_states`, `document_files` (raw bytes), other users' rows.

### 3.2 Role

`bill_locker_ai`: `NOLOGIN`, `SELECT` on the six `ai_*` views only, no rights on any table.
Views run with the owner's rights, so the role reads through the view without touching the table.

The view filter trusts the `app.user_id` setting, so the AI's SQL must not be able to change it:
`REVOKE EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) FROM PUBLIC` and
`REVOKE UPDATE ON pg_catalog.pg_settings FROM PUBLIC`, then `GRANT EXECUTE … TO CURRENT_USER`
(the app's own DB user, which sets the value). Without this, `WITH x AS (SELECT set_config('app.user_id',
'<other user>', true)) SELECT …` would show another user's rows. The text check also rejects SQL
containing `set_config` or `pg_settings`.

### 3.3 `AssistantDatabaseSetup` (startup, idempotent)

Runs on `ApplicationReadyEvent` (after Hibernate created the tables):
create the role if missing (`DO $$ … IF NOT EXISTS … CREATE ROLE … $$`), `CREATE OR REPLACE VIEW`
for each view, `GRANT SELECT … TO bill_locker_ai`. Plain Java strings, no migration files
(the user rejected Flyway). Needs the app's DB user to be allowed to create roles (local `postgres`
and Testcontainers are). If it fails, log a clear warning; the assistant then answers with an error,
the rest of the app keeps working.

### 3.4 `ReadOnlyQueryRunner.run(UUID userId, String sql)` → `List<Map<String,Object>>`

1. Check text: trimmed SQL starts with `select` or `with` (case-insensitive), contains no `;`
   (one trailing `;` is stripped first), and doesn't contain `set_config` or `pg_settings`.
   Else `QueryRejectedException`.
2. In one transaction (`TransactionTemplate`, read-only):
   `SET TRANSACTION READ ONLY` → `SET LOCAL statement_timeout = '3s'` →
   `SELECT set_config('app.user_id', ?, true)` → `SET LOCAL ROLE bill_locker_ai` →
   run the query with `setMaxRows(50)`.
3. Transaction ends (rolled back): role and setting are gone before the connection returns to the pool.
4. Postgres errors (unknown column, permission denied, timeout, read-only violation) become
   `QueryRejectedException(message)` so Gemini can try once more.

Real safety comes from the database (read-only + role + view filter); the text check only
stops obvious nonsense early.

## 4. Backend flow

### 4.1 Endpoint

`POST /api/assistant/chat` (authenticated)

```json
// request
{ "messages": [ { "role": "user", "text": "How much did I spend this month?" } ] }
// response 200
{ "success": true, "answer": "You spent ₹4,820 this month across 6 bills…", "sql": "SELECT …" }
```

Validation: 1–10 messages, `role` is `user` or `assistant`, `text` 1–2000 chars, last message is
`user` → else 400 `VALIDATION_FAILED` with `fieldErrors`.
`sql` is `null` when no query ran (greeting, refusal).

### 4.2 Steps (`AssistantService.chat(userId, messages)`)

1. **Plan** — `AssistantModel.plan(messages, today)` → `Plan { action: QUERY | REPLY, sql, reply }`.
   Gemini answers with a JSON response schema (same trick as `GeminiDetailExtractor`).
   - `REPLY`: return `reply` as the answer, `sql = null`.
2. **Query** — `ReadOnlyQueryRunner.run(userId, sql)`. On `QueryRejectedException`, call
   `plan` once more with the error appended; if it fails again, answer
   "I couldn't look that up. Try asking in a different way." (`sql` = last attempt).
3. **Answer** — `AssistantModel.answer(messages, sql, rows)` → plain text.

No `@Transactional` around the Gemini calls: only the query runs in a transaction.

### 4.3 Prompt rules

Plan prompt: role ("You answer questions about the user's bills in Bill Locker"), today's date,
the six views with columns and meaning (status values, document types, categories, Gmail file
statuses), and rules:
- Only one `SELECT` on `ai_*` views. Never filter by user, the views already do.
- "Spent" = `SUM(total_amount)` on `ai_documents` with `status = 'SAVED'`, by `purchase_date`.
- Name search is case-insensitive `ILIKE '%…%'` on product name, brand, seller name, file name.
- Use `CURRENT_DATE` for relative dates. Include `ai_documents.id` when showing specific bills.
- Requests to delete, upload, edit, save, connect or send anything → `REPLY` saying you can only
  look things up, and which page does it (Documents, Gmail import, Settings).
- Questions not about the user's data → short `REPLY`.

Answer prompt: answer only from the rows; no rows → say nothing matched; amounts as ₹ with
Indian grouping; dates like "7 Oct 2026"; link a bill as `/documents/<id>`; short, plain text,
no Markdown tables.

### 4.4 Code layout (package `assistant`)

| Class | Job |
|---|---|
| `AssistantController` | HTTP ↔ Java, user id from JWT |
| `ChatRequest`, `ChatMessage`, `ChatResponse` | records |
| `AssistantService` | the three steps above |
| `AssistantModel` (interface) + `Plan` | plan / answer |
| `GeminiAssistantModel` | Gemini REST calls, backup models on 503/429 (like `GeminiDetailExtractor`) |
| `AssistantModelConfig` | real model when `GEMINI_API_KEY` is set, else one that throws "not configured" |
| `AssistantDatabaseSetup` | views + role at startup |
| `ReadOnlyQueryRunner` + `QueryRejectedException` | guarded query |
| `AssistantException` | `NOT_CONFIGURED` / `FAILED` |

### 4.5 Errors

| Case | HTTP | Code | Message |
|---|---|---|---|
| No Gemini key | 503 | `AI_NOT_CONFIGURED` | The assistant isn't set up (no GEMINI_API_KEY). |
| Gemini busy / quota / unreachable | 503 | `ASSISTANT_FAILED` | Gemini's message, e.g. "Gemini is busy right now…" |
| SQL failed twice | 200 | — | "I couldn't look that up…" as the answer |

## 5. Frontend

- `components/assistant/AssistantChat.tsx`, rendered by `AppLayout.tsx` (so messages survive
  page changes; refresh/sign-out clears them).
- Closed: round button bottom-right (`MessageCircle`, `aria-label="Open assistant"`), above the
  phone tab bar on small screens.
- Clicking the button toggles the panel open/closed.
- Open: panel ~380×560 (full screen on phones). Header "Ask to your locker", "New chat", close ✕.
  Messages (user right, assistant left), "Looking it up…" while waiting, 3 example questions when
  empty, textarea (Enter sends, Shift+Enter new line) + Send button.
- Each answer with `sql` has a "Show query" toggle that shows the SQL in a `<pre>`.
- Answers are plain text, never HTML. `/documents/<uuid>` in the text becomes a React Router
  `<Link>`.
- Errors appear as an assistant bubble (`AI_NOT_CONFIGURED`, `ASSISTANT_FAILED`, network).
- `lib/api.ts`: `askAssistant(messages)` → `POST /api/assistant/chat`.
- Esc closes and returns focus to the button; message list is `aria-live="polite"`.

## 6. Testing

MockMvc + Testcontainers, `FakeAssistantModel` (returns a set plan/answer; records calls), Gemini
never called:
- User A's chat with `SELECT * FROM ai_documents` returns only A's rows (B has bills too).
- `DELETE FROM documents` → rejected by the text check; rows still there.
- `WITH x AS (DELETE FROM documents RETURNING *) SELECT * FROM x` → rejected by the database
  (read-only); rows still there.
- `SELECT * FROM users`, `SELECT password_hash FROM users` → permission denied (role).
- `SELECT 1; DROP TABLE documents` → rejected.
- A `set_config('app.user_id', '<B>', true)` trick inside a `WITH` → rejected; never B's rows.
- Bad column on first plan → second plan used (fake records two calls).
- `REPLY` plan → answer returned, `sql` null, no query run.
- Validation: empty list, 11 messages, last message from assistant → 400.
- Without a token → 401.

Frontend: `npm run typecheck` + `npm run build`, manual check in the browser at desktop and 375 px.

## 7. Docs

- `docs/task-6-assistant.md` — what happens where, step by step.
- `docs/api-contract.md` — add `POST /api/assistant/chat`.
- `CLAUDE.md` — add task 6 to "Tasks done".
