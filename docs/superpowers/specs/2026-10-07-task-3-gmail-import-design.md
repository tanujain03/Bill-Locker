# Task 3 — Gmail import (design)

Date: 2026-10-07 · Branch: `restart` · Builds on task 2 (documents + Gemini reading).

## 1. Goal

Connect one or more Gmail accounts to a Bill Locker user. Bill Locker scans them
(read-only) for emails with bills attached (invoices, guarantee/warranty cards, service
receipts, receipts). The user sees each found file and chooses **Import** or
**Ignore**. Imported files become documents and are read by Gemini in the background,
then reviewed and saved exactly like uploads.

**Done when:** a user can connect two Gmail accounts, scan each with a chosen time
range, import some files and ignore others, and find the imported ones in Documents as
"Reading…" → "Needs review", with all API tests green and Google/Gemini never called in
tests.

## 2. Decisions (agreed with the user)

| Topic | Decision |
|---|---|
| Background work | **Approach A:** the database is the queue; `@Scheduled` workers poll every 3 s. Survives restarts; tests call `runOnce()` |
| AI reading after import | In the background, **one document at a time** (Gemini free limit) |
| When scans run | Right after connecting (range: 1 year) + a **Scan** button per account |
| Time range | The user picks per scan: 6 months / 1 year / 2 years / 5 years |
| Finding bills | Gmail search + keyword rules. No Gemini for sorting emails |
| Unit of choice | **Each file** (attachment) has Import / Ignore |
| Ignore | Moves to an **Ignored** tab; never offered again by scans; can be imported later |
| Accounts | Several per user; the same address connected again updates its token |

Out of scope (later): emails whose bill is only in the email text (no attachment), a
daily automatic scan, notifications, linking documents to products/warranties, moving
uploads to the background reader.

## 3. Data model

All new tables, so `ddl-auto=update` creates them. They use new names, so the old `dev`
tables (`gmail_connections`, `gmail_messages`, `gmail_oauth_states`) in the local DB are
left alone. No enum values are added to existing columns: Hibernate created CHECK
constraints for `documents.status` / `document_type`, and `update` would not widen them.

### `gmail_accounts` (`GmailAccount`)
| Column | Type | Notes |
|---|---|---|
| `id`, `created_at`, `updated_at` | | `BaseEntity` |
| `user_id` | uuid, FK `fk_gmail_accounts_user`, on delete cascade | |
| `email` | varchar(254) | the Gmail address; unique with user: `uk_gmail_accounts_user_email` |
| `refresh_token_ciphertext` | bytea | AES-256-GCM (nonce + ciphertext), key `GMAIL_TOKEN_KEY`. The plain token is never stored; access tokens are never stored |
| `scan_status` | varchar enum `GmailScanStatus` | `IDLE`, `QUEUED`, `SCANNING`, `ERROR` |
| `scan_since` | date, null | the start of the range the queued scan covers |
| `last_scanned_at` | timestamptz, null | |
| `last_error` | varchar(500), null | words the user can act on |

### `gmail_connect_states` (`GmailConnectState`)
One-time link between "Connect" and Google's redirect.
`id`, `user_id` (FK, cascade), `state_hash` (SHA-256 hex, unique), `code_verifier`
(PKCE secret, varchar 128), `expires_at` (10 minutes). Deleted when used or expired.

### `gmail_emails` (`GmailEmail`)
| Column | Notes |
|---|---|
| `account_id` | FK `fk_gmail_emails_account`, on delete cascade |
| `user_id` | FK, cascade — so every lookup is `findByIdAndUserId` like elsewhere |
| `gmail_message_id` | varchar(64); unique with account: `uk_gmail_emails_account_message` (later scans skip it) |
| `from_name`, `from_email` | varchar(200) / (254) |
| `subject`, `snippet` | varchar(500) each; Gmail's short preview. **Never the full email body** |
| `received_at` | timestamptz; index `idx_gmail_emails_user_received` (user_id, received_at desc) |
| `kind` | varchar enum `EmailKind`: `INVOICE`, `WARRANTY`, `SERVICE`, `RECEIPT`, `ORDER`, `UNSURE` |

### `gmail_files` (`GmailFile`)
| Column | Notes |
|---|---|
| `email_id` | FK `fk_gmail_files_email`, cascade |
| `part_id` | varchar(32): the attachment's MIME part id, stable across fetches (Gmail's attachment ids are not) |
| `file_name`, `content_type`, `size_bytes` | from Gmail's message metadata |
| `status` | varchar enum `GmailFileStatus`: `NEW`, `IGNORED`, `IMPORTING`, `IMPORTED`, `FAILED` |
| `document_id` | FK `fk_gmail_files_document`, **on delete set null**, null until imported |
| `error` | varchar(500), null: why import failed |

### `documents` — three new nullable columns
- `read_queued_at` timestamptz: set = waiting for the background AI read. Index
  `idx_documents_read_queued` on it.
- `read_error` varchar(500): why the last background read failed.
- `source_gmail` varchar(254): the Gmail address an imported document came from; null
  for uploads. (Stored, so the `document` package needn't look into `gmail`.)

The document `status` keeps UPLOADED → EXTRACTED → SAVED. "Reading…" in the UI means
`readQueuedAt != null`. `DocumentDetail` and `DocumentSummary` get `readQueued`
(boolean), `readError` and `sourceGmail` (the address, or null for uploads).

## 4. Backend

Package `project.bill_locker.gmail`. Controller → service → repository, records for
request/response, `ApiException` for errors, user id from the token only.

### 4.1 Configuration
`app.gmail.client-id=${GOOGLE_CLIENT_ID:}`, `client-secret=${GOOGLE_CLIENT_SECRET:}`,
`token-key=${GMAIL_TOKEN_KEY:}` (base64, 32 bytes),
`redirect-uri=${GMAIL_REDIRECT_URI:http://localhost:8080/api/integrations/gmail/callback}`
(the URI already registered in the user's Google client), `app.frontend-url` (exists).
`GmailProperties` (record, `@ConfigurationProperties`) with `isConfigured()`. At start-up
the log says which key is missing or malformed, e.g. `Gmail import is off:
GMAIL_TOKEN_KEY is not set`. When not configured every Gmail endpoint except `GET`
answers `503 GMAIL_NOT_CONFIGURED`; `GET` answers `{ configured: false, … }`.
`@EnableScheduling` on a small config class. `app.workers.enabled=true` (tests set it
to `false` and call `runOnce()` themselves).

### 4.2 Talking to Google: `GoogleApi` (interface) + `GoogleRestApi` (RestClient)
Same split as `DetailExtractor` / `GeminiDetailExtractor`; tests swap in
`FakeGoogleApi`. Methods:
- `authorizationUrl(state, codeChallenge)`: scope `gmail.readonly`, `access_type=offline`,
  `prompt=consent select_account` (so another account can be chosen and a refresh token
  is always returned), PKCE S256.
- `exchangeCode(code, verifier)` → `Tokens(accessToken, refreshToken)`
- `accessToken(refreshToken)`, `emailAddress(accessToken)`
- `searchMessages(accessToken, query, max)` → ids (pages through `nextPageToken`)
- `message(accessToken, id)` → `Email(id, from, subject, snippet, receivedAt, attachments[])`
  with `Attachment(partId, attachmentId, fileName, contentType, sizeBytes)`; only
  `format=full` metadata and parts, the body text is not kept.
- `attachment(accessToken, messageId, attachmentId)` → bytes (base64url decoded)
- `revoke(refreshToken)`
- Errors → `GoogleApiException(message, accessRevoked)`; `invalid_grant` / 401 means
  access was withdrawn.

`TokenCipher`: AES-256-GCM, random 12-byte nonce per encryption.

### 4.3 Endpoints (`/api/integrations/gmail`)
| Method + path | Body → answer |
|---|---|
| `GET /` | `{ configured, accounts: GmailAccountView[], counts: { toReview, ignored, imported } }` |
| `POST /connect` | → `{ authorizationUrl }` |
| `GET /callback?code&state[&error]` | **public** (added to `SecurityConfig` for GET). Always a `302` to `{FRONTEND_URL}/gmail?connected=<email>` or `?error=<reason-code>` (`denied`, `expired`, `failed`); never Google's error details |
| `POST /accounts/{id}/scan` | `{ range: SIX_MONTHS \| ONE_YEAR \| TWO_YEARS \| FIVE_YEARS }` → `GmailAccountView` (`QUEUED`). `409 GMAIL_SCAN_RUNNING` if already queued/scanning |
| `DELETE /accounts/{id}` | `204`. Revokes at Google (errors ignored: the user may have revoked already), deletes the account, its emails and files. Imported documents stay |
| `GET /emails?view=TO_REVIEW\|IGNORED\|IMPORTED` | `GmailEmailView[]`, newest first. Each email lists only its files in that view. TO_REVIEW = `NEW`, `IMPORTING`, `FAILED`; IGNORED = `IGNORED`; IMPORTED = `IMPORTED` (with the document's id, status and `readQueued`) |
| `POST /files/import` | `{ fileIds: uuid[] }` (1–100) → `GmailFileView[]`. `NEW`, `IGNORED`, `FAILED`, and `IMPORTED` whose document was deleted → `IMPORTING`; others unchanged |
| `POST /files/ignore` | `{ fileIds }` → `GmailFileView[]`. `NEW`, `FAILED` → `IGNORED` |
| `POST /files/restore` | `{ fileIds }` → `GmailFileView[]`. `IGNORED` → `NEW` |

Another user's account or file id → `404 GMAIL_ACCOUNT_NOT_FOUND` / `GMAIL_FILE_NOT_FOUND`
(file id lists: any unknown id → 404, nothing changed).

`GmailAccountView`: `id, email, scanStatus, lastScannedAt, lastError, connectedAt`.
`GmailEmailView`: `id, accountEmail, fromName, fromEmail, subject, snippet, receivedAt,
kind, files[]`. `GmailFileView`: `id, fileName, contentType, sizeBytes, status, error,
document: { id, status, readQueued } | null`.

### 4.4 Connect flow
1. `POST /connect`: random 32-byte state + PKCE verifier; store `sha256(state)`,
   verifier, `expires_at = now + 10 min` for the user; delete that user's expired states.
2. Callback: look up by `sha256(state)`, check not expired, delete it (one use). Exchange
   the code with the verifier, get the address, encrypt the refresh token, then
   upsert `gmail_accounts` by (user, email) and queue a 1-year scan. Google calls happen
   outside DB transactions.
3. If Google did not return a refresh token, redirect with `error=failed` (should not
   happen with `prompt=consent`).

### 4.5 `GmailScanWorker` (every 3 s, one account per run)
- Service claims the oldest `QUEUED` account → `SCANNING` (short transaction), and gives
  the worker its refresh token (decrypted), `scan_since` and the set of known message ids.
- Query: `after:YYYY/MM/DD has:attachment (invoice OR receipt OR bill OR warranty OR
  guarantee OR service OR "order confirmation" OR "tax invoice")`. Newest 200 at most.
- For each unknown id: fetch the message; keep attachments whose type is PDF / JPEG /
  PNG / WebP (by `contentType` or file extension) and size ≤ 10 MB; no such attachment →
  skip the email.
- `EmailClassifier` (rules, whole words, lower-case, file names with `_ . -` turned into
  spaces) gives `kind` or "drop":
  - junk words (`statement`, `newsletter`, `unsubscribe`, `otp`, `one time password`,
    `offer`, `sale`, `discount`, `payslip`, `salary slip`) and no file named like an
    invoice → **drop**
  - `warranty`, `guarantee` → `WARRANTY`; `service`, `repair`, `job card` → `SERVICE`;
    `invoice`, `tax invoice`, `bill of supply` → `INVOICE`; `receipt`, `cash memo`,
    `payment received` → `RECEIPT`; `order confirmation`, `your order`, `has shipped`,
    `delivered` → `ORDER`; otherwise `UNSURE` (kept, the user decides).
- Save emails + files (status `NEW`) in one short transaction; `IDLE`, `last_scanned_at`.
- Errors: access revoked → `ERROR` "Gmail access was removed. Connect this account
  again."; Google unreachable → `ERROR` "Gmail could not be reached. Try Scan again
  later."; anything else → `ERROR` "The scan failed. Please try again." (logged).
- On start-up, accounts stuck in `SCANNING` (backend stopped mid-scan) go back to `QUEUED`.

### 4.6 `GmailImportWorker` (every 3 s, up to 5 files per run)
- Claims `IMPORTING` files (oldest first). Per file, outside a transaction: access token
  → fetch the message → find the attachment by `part_id` → download.
- Then `DocumentService.createFromBytes(userId, fileName, bytes, queueForReading=true)`
  — the same checks as upload (`FileType` magic bytes, 10 MB), sets `read_queued_at`.
  The upload endpoint keeps its own path and stays as today.
- Success → `IMPORTED` + `document_id`. Not a real PDF/image, too big, or gone from
  Gmail → `FAILED` with a reason ("This file isn't a PDF or image Bill Locker can read").
  Access revoked → `FAILED` + the account goes to `ERROR`.
- On start-up nothing is stuck: `IMPORTING` simply stays claimable.

### 4.7 `DocumentReadWorker` (package `document`, every 3 s, one document per run)
- Picks the oldest document with `read_queued_at` set and calls the existing extract
  logic (short transaction → `DetailExtractor` → short transaction).
- Success → `EXTRACTED`, `read_queued_at = null`, `read_error = null`.
- `ExtractionException` → `read_queued_at = null`, `read_error = <its message>`
  (status stays `UPLOADED`; the user can press **Read with AI**). AI not configured →
  same, with the "AI reading is not set up" message.
- Manual `POST /documents/{id}/extract`, `PUT` (save) and delete on a queued document
  clear `read_queued_at` first, so the worker never overwrites the user's work.

## 5. Frontend

- `lib/gmail.ts`: types + one function per endpoint (through `lib/api.ts`).
- `AppHeader`: new **Gmail** link. Route `/gmail` → `pages/GmailPage.tsx` (RequireAuth).
- **Not configured:** an info panel listing the three `.env` keys and a link to the
  guide section.
- **Accounts** (`components/gmail/AccountCard.tsx`): address, status line
  ("Last scanned 5 min ago" / spinner "Scanning…" / red error with icon), **Scan ▾**
  (menu: 6 months, 1 year, 2 years, 5 years), **Disconnect** (confirm: "Imported bills
  stay in Documents"). **+ Connect a Gmail account** button (first time: a short
  explanation of what Bill Locker reads and that access is read-only).
- After Google: `?connected=` → success alert "Connected a@gmail.com. Scanning the last
  year…"; `?error=` → error alert with a plain explanation; the query is then removed.
- **Found files** (`components/gmail/EmailCard.tsx`): tabs **To review (n) · Ignored (n)
  · Imported (n)**. Each email: kind badge (icon + label), sender, subject, date, account
  address, one-line preview. Its files: checkbox, file icon, name, size, and per state:
  New → **Import** / **Ignore**; Importing → spinner "Importing…"; Failed → red reason +
  **Retry** / **Ignore**; Ignored → **Import** / **Restore**; Imported → link "Open
  document" + `StatusBadge` ("Reading…" when `readQueued`); if that document was
  deleted from Documents → "Deleted from Documents" + **Import again**.
- Ticking files shows a sticky bar: **Import selected (n)** / **Ignore selected (n)**.
- Empty states: no accounts ("Connect Gmail to find bills in your inbox"), nothing to
  review ("All caught up").
- **Polling:** while any account is `QUEUED`/`SCANNING` or any file is `IMPORTING` or
  any imported document is `readQueued`, the page reloads its data every 3 s.
- **Documents:** `StatusBadge` gets a "Reading…" state (spinner icon) when
  `readQueued`; list rows show a small "Gmail" hint for Gmail documents; the list polls
  every 3 s while any row is reading. `DocumentPage` shows `readError` as an alert with
  **Read with AI**, and "Reading with AI…" while queued (polling until done).

## 6. Security & privacy

- Scope `gmail.readonly` only. No sending, no deleting, no labels changed.
- Refresh tokens encrypted at rest; access tokens and tokens in general never logged
  and never sent to the browser.
- State: one use, 10 minutes, stored hashed, bound to the user who clicked Connect.
- The callback is the only unauthenticated Gmail endpoint and only ever redirects to
  `FRONTEND_URL`.
- Stored per email: sender, subject, Gmail's preview, date, attachment metadata. Not
  the body. Files are downloaded only when the user imports them.
- All lookups by `(id, userId)`; another user's ids are 404.
- Disconnect revokes the token at Google.

## 7. Errors (codes)

`503 GMAIL_NOT_CONFIGURED`, `404 GMAIL_ACCOUNT_NOT_FOUND`, `404 GMAIL_FILE_NOT_FOUND`,
`409 GMAIL_SCAN_RUNNING`, `400 VALIDATION_FAILED` (bad range, empty / too many ids).

## 8. Tests (`mvnw test`, Testcontainers; Google and Gemini never called)

- `FakeGoogleApi` (test bean): in-memory mailboxes per address, records revokes, can be
  told to fail (revoked / unreachable).
- `GmailConnectApiTests`: connect gives a Google URL with state + PKCE; callback with a
  good state → account + redirect `?connected=`; expired, reused or unknown state →
  `?error=expired`; `error=access_denied` → `?error=denied`; two accounts for one user;
  same address again → one account, new token; callback is reachable without a login.
- `GmailScanTests`: scan (via `runOnce()`) saves only emails with PDF/image files;
  second scan adds only new ones; range becomes the right `after:` date; junk dropped;
  kinds right; revoked → `ERROR` with the message; stuck `SCANNING` reset at start-up.
- `EmailClassifierTests` (plain unit tests): the rules table above.
- `GmailImportTests`: import → `IMPORTED` + document queued; read worker → `EXTRACTED`
  via `FakeDetailExtractor`; fake file (text named .pdf) → `FAILED`; ignore → restore →
  import; ignored files not offered again by a new scan; disconnect deletes the list,
  keeps documents, revokes; other user's account/file → 404.
- `DocumentReadWorkerTests`: extraction failure → `read_error`, status `UPLOADED`;
  manual extract / save clears the queue.
- `TokenCipherTests`: round trip; tampered ciphertext fails.
- Frontend: `npm run typecheck` + `npm run build`; manual check in the browser.

## 9. Docs

- `docs/task-3-gmail.md` (guide in the style of task 1/2): what happens where, the Google
  Cloud setup (reuse the existing client; **every Gmail address to connect must be a Test
  user** while the app is in Testing; Testing tokens expire after 7 days), common
  problems table, tests.
- `docs/api-contract.md` §13 rewritten for multiple accounts and per-file import; top
  "built so far" list updated. `backend/.env.example`: the Gmail keys with comments.
  `CLAUDE.md`: Task 3 entry.
