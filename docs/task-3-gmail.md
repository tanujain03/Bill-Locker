# Task 3 — Gmail import: connect, scan, review, import, read

You connect one or more Gmail addresses (read-only). Bill Locker scans the newest
emails for attachments that look like bills, and shows them on **/gmail** in three
tabs: **To review**, **Ignored**, **Imported**. You import the files you want (one by
one or many at once). Each import becomes a normal document and is read with Gemini
**in the background**; you review it like any upload.

This guide says **what happens where**, so you can follow a click to the database and
back. It builds on [`task-2-documents.md`](task-2-documents.md).

## 1. The big picture

```
Browser (React, :5173)                Spring Boot (:8080)                       PostgreSQL / Google
──────────────────────                ───────────────────                       ───────────────────
GmailPage: "Connect Gmail"
  POST /integrations/gmail/connect ─▶ GmailConnectService.connect
                                        random state + PKCE verifier,
                                        random browser nonce ───────────────▶ gmail_connect_states
  ◀─ { authorizationUrl } + Set-Cookie gmail_connect (HttpOnly, 10 min)
window.location = authorizationUrl ──────────────────────────────────────▶ Google consent page
Google ─▶ GET /api/integrations/gmail/callback?code&state (cookie comes along)
                                      GmailConnectService.callback
                                        state used once, cookie must match,
                                        code → tokens at Google, ask the address,
                                        refresh token ENCRYPTED ───────────▶ gmail_accounts (scan QUEUED)
  ◀─ 302 /gmail?connected=you@gmail.com   (or ?error=denied|expired|failed)

GmailScanWorker (every 3 s)            claimNextScan: QUEUED → SCANNING ◀──▶ gmail_accounts
  no transaction while talking to Google:
    refresh token → access token ────▶ Google
    search newest 200 emails ────────▶ Gmail API
    fetch each new email, keep bill-like attachments, classify
  saveScan (short transaction) ───────▶ gmail_emails, gmail_files (NEW); account IDLE
GmailPage polls GET /api/integrations/gmail (the overview) + /emails while a scan is QUEUED/SCANNING

You tick files → "Import"
  POST /files/import {fileIds} ─────▶ GmailService.importFiles
                                        NEW/IGNORED/FAILED → IMPORTING ─────▶ gmail_files
GmailImportWorker (every 3 s)          claimImports (5 per run)
    download the attachment ─────────▶ Gmail API
    DocumentService.createFromBytes ──▶ documents (UPLOADED, read_queued_at set,
                                        source_gmail), document_files
    importDone: file IMPORTED + document link ──▶ gmail_files

DocumentReadWorker (every 3 s)         readNextQueued: oldest queued document
    Gemini reads it (no transaction) ▶ Gemini
    store details, status EXTRACTED, queue cleared ──▶ documents, document_items
/documents shows "Reading…" until then, then "Needs review" → you check and save (task 2)
```

Why so many steps instead of doing it all in the click? Google and Gemini can take
seconds (or fail). The click only changes a row in the database and answers at once.
A worker picks the row up later. See section 5.

## 2. Files: backend (`backend/src/main/java/project/bill_locker/`)

| File | Job |
|---|---|
| `gmail/GmailController.java` | HTTP ↔ Java: the 9 endpoints (section 6). Sets and clears the `gmail_connect` cookie |
| `gmail/GmailService.java` | The rules: overview, queue a scan, save scan results, the three tabs, import / ignore / restore, what the workers claim |
| `gmail/GmailConnectService.java` | Connect and disconnect: state, PKCE, browser cookie, code exchange, token encryption, revoke |
| `gmail/GmailScanWorker.java` | Scans one queued mailbox per run; which attachments are offered |
| `gmail/GmailImportWorker.java` | Downloads up to 5 chosen files per run and creates the documents |
| `gmail/EmailClassifier.java`, `EmailKind.java` | Keyword rules: INVOICE / WARRANTY / SERVICE / RECEIPT / ORDER / UNSURE, or drop junk |
| `gmail/GoogleApi.java` | Interface: everything we ask Google. Tests swap it for `FakeGoogleApi` |
| `gmail/GoogleRestApi.java` | The real one (`RestClient`): sign-in URL, token calls, Gmail search / message / attachment, revoke. Turns errors into `GoogleApiException` kinds |
| `gmail/TokenCipher.java` | AES-256-GCM for the refresh token, key `GMAIL_TOKEN_KEY` |
| `gmail/GmailProperties.java`, `GmailConfig.java` | The four settings, `isConfigured()`, and the start-up log line saying whether Gmail is on |
| `gmail/GmailAccount.java` | Entity → `gmail_accounts`: address, encrypted token, scan status |
| `gmail/GmailEmail.java` | Entity → `gmail_emails`: sender, subject, short preview, date, kind. Never the body |
| `gmail/GmailFile.java` | Entity → `gmail_files`: one attachment and its status (+ link to the document) |
| `gmail/GmailConnectState.java` | Entity → `gmail_connect_states`: a sign-in in progress |
| `gmail/GmailFileStatus.java`, `GmailScanStatus.java` | The two enums (section 3) |
| `gmail/Gmail*Repository.java` | Spring Data queries (`findByIdAndUserId`, `findForView`, `consume` …) |
| `gmail/GmailViews.java` | All request / response records and the two small enums `ScanRange`, `EmailView` |
| `common/WorkerSchedule.java` | The one place with the timers: three `@Scheduled` methods, every 3 s. Off when `app.workers.enabled=false` |
| `document/DocumentReadWorker.java` | Calls `DocumentService.readNextQueued()` |
| `document/DocumentService.java` | New: `createFromBytes` (import), `readNextQueued` (background read) |
| `document/Document.java` | New nullable columns `read_queued_at`, `read_error`, `source_gmail` |
| `security/SecurityConfig.java` | `GET /api/integrations/gmail/callback` is public (it has no login token) |

## 3. Files: frontend (`frontend/src/`)

| File | Job |
|---|---|
| `lib/gmail.ts` | Types matching `GmailViews`, labels, one function per endpoint, `CALLBACK_ERRORS`, `formatSize`, `timeAgo` |
| `lib/usePolling.ts` | `usePolling(active, refresh, ms)`: calls `refresh` every 3 s while `active`, skips while the tab is hidden |
| `pages/GmailPage.tsx` | `/gmail`: accounts, tabs with counts, select + Import / Ignore / Restore, the notice after Google's redirect |
| `components/gmail/AccountCard.tsx` | One connected address: scan status, Scan, Disconnect |
| `components/gmail/ScanMenu.tsx` | Choose how far back to scan (6 months … 5 years) |
| `components/gmail/EmailCard.tsx` | One email with its files, each with its status and error |
| `pages/DocumentsPage.tsx`, `DocumentPage.tsx`, `components/documents/StatusBadge.tsx` | "Reading…" while `readQueued`, polling until it turns "Needs review", "From Gmail · address", the saved `readError` |
| `components/AppHeader.tsx`, `App.tsx` | The Gmail link and route |

## 4. Database

Hibernate creates the new tables and columns from the entities (`ddl-auto=update`).
Only **new tables** and **new nullable columns** were added: `update` can't safely
change existing columns, and the existing enum columns are not touched.

| Table | Rows | Columns |
|---|---|---|
| `gmail_accounts` | one per connected address | `user_id`, `email` (unique per user), `refresh_token_ciphertext`, `scan_status`, `scan_since`, `last_scanned_at`, `last_error` |
| `gmail_emails` | one per email found | `account_id`, `user_id`, `gmail_message_id` (unique per account), `from_name`, `from_email`, `subject`, `snippet`, `received_at`, `kind` |
| `gmail_files` | one per attachment | `email_id`, `part_id`, `file_name`, `content_type`, `size_bytes`, `status`, `document_id` (nullable), `error` |
| `gmail_connect_states` | one per sign-in in progress | `user_id`, `state_hash` (unique), `code_verifier`, `browser_hash`, `expires_at` |
| `documents` (new columns) | | `read_queued_at`, `read_error`, `source_gmail` (all nullable) |

Statuses:

- **Scan** (`gmail_accounts.scan_status`): `IDLE` → `QUEUED` → `SCANNING` → `IDLE`, or `ERROR` (with `last_error`).
- **File** (`gmail_files.status`): `NEW` → `IMPORTING` → `IMPORTED`; or `IGNORED`; or `FAILED` (with `error`).
- **Tabs** are just groups of file statuses: To review = `NEW` + `IMPORTING` + `FAILED`, Ignored = `IGNORED`, Imported = `IMPORTED`. The counts on the tabs count files, not emails.

Details worth knowing:

- Why files are a separate table: one email can carry several attachments, and each is
  imported or ignored on its own.
- Only sender, subject, Gmail's short preview, date and file names are stored. **Never
  the email's text**, and a file's bytes are downloaded only when you import it.
- Disconnecting deletes the account, its emails and files (`ON DELETE CASCADE`).
  Imported documents stay: `gmail_files.document_id` is `SET NULL` when a document is
  deleted, and the file row survives (it can be imported again).
- `documents.read_queued_at` is the reading queue (section 5). `source_gmail` is the
  address a bill came from (null for uploads).

## 5. The workers, and why the database is the queue

Three workers, all started by `WorkerSchedule` with `fixedDelay = 3000`: the next run
starts 3 s **after the last one ends**, so a worker never overlaps with itself. They
share one scheduler thread, so they take turns.

| Worker | Takes | Does |
|---|---|---|
| `GmailScanWorker` | the oldest account with `scan_status = QUEUED` | `SCANNING`, searches Gmail, saves emails + files, `IDLE` (or `ERROR` + `last_error`) |
| `GmailImportWorker` | up to 5 files with `status = IMPORTING`, oldest first | downloads, creates the document, file → `IMPORTED` (or `FAILED` + `error`) |
| `DocumentReadWorker` | the document with the oldest `read_queued_at` | Gemini reads it, details stored, status `EXTRACTED`, queue cleared (or `read_error`) |

**Why the database is the queue:** "queued" is just a column value. There is no
message broker and nothing to install. If the app restarts, the queued work is still
there. On start-up, accounts left in `SCANNING` (a scan cut off by the restart) are put
back to `QUEUED`. Files stay `IMPORTING`, and documents keep `read_queued_at`, so they
simply continue.

**Why the slow calls are outside transactions:** the same pattern as Gemini in task 2.
A worker does short transaction (claim the job) → Google/Gemini (no transaction) →
short transaction (store the result). The worker is the only place that calls Google;
`GmailService` and `DocumentService` hold the short database steps.

**Failures never block the queue:**

- A failed file or document is marked `FAILED` / given a `read_error` and the worker moves on to the next.
- If Gmail is unreachable during an import, the file is left `IMPORTING` and the next run tries again.
- If the token can't be decrypted (the key changed), that job is marked failed at once. Otherwise the same job would sit at the front of the queue forever.
- If you disconnect or delete while a worker is busy, its result is dropped (the row is gone).
- If you read or save a document yourself while it waits, that clears `read_queued_at`; the worker's late answer is ignored (it only stores when the queue time still matches).

Without Gmail setup, the Gmail workers do nothing (`isConfigured()` is checked first).
In tests the timers are off (`app.workers.enabled=false`); tests call `runOnce()`.

## 6. Endpoints

All need `Authorization: Bearer <token>`, except the callback. Another user's account
or file → `404`. When Gmail isn't set up: `503 GMAIL_NOT_CONFIGURED` on connect, scan, import, ignore,
restore and disconnect (except `GET` overview, which answers `configured: false`, and
`GET` emails, which just lists what exists).

| Endpoint | Answer |
|---|---|
| `GET /api/integrations/gmail` | `200 GmailOverview`: `configured`, `accounts`, `counts` |
| `POST /api/integrations/gmail/connect` | `200 { authorizationUrl }` + `Set-Cookie: gmail_connect`. `503 GMAIL_NOT_CONFIGURED` |
| `GET /api/integrations/gmail/callback` | **public**. Always `302` to `{FRONTEND_URL}/gmail?connected=<address>` or `?error=denied\|expired\|failed`; clears the cookie |
| `POST /api/integrations/gmail/accounts/{id}/scan` `{ "range": "ONE_YEAR" }` | `200 GmailAccountView` (now `QUEUED`). `409 GMAIL_SCAN_RUNNING`, `404 GMAIL_ACCOUNT_NOT_FOUND`. Ranges: `SIX_MONTHS`, `ONE_YEAR`, `TWO_YEARS`, `FIVE_YEARS` |
| `GET /api/integrations/gmail/emails?view=TO_REVIEW\|IGNORED\|IMPORTED` | `200 GmailEmailView[]`, newest first, each with only its files in that tab. `400 INVALID_VIEW` |
| `POST /api/integrations/gmail/files/import` `{ "fileIds": [...] }` | `200 GmailFileView[]`: `NEW`/`IGNORED`/`FAILED` (and `IMPORTED` ones whose document was deleted) become `IMPORTING` |
| `POST /api/integrations/gmail/files/ignore` | `NEW`/`FAILED` → `IGNORED` |
| `POST /api/integrations/gmail/files/restore` | `IGNORED` → `NEW` |
| `DELETE /api/integrations/gmail/accounts/{id}` | `204`. Revokes at Google (best effort), deletes the account, its emails and files |

- `fileIds`: 1 to 100 ids. All ids are checked first: one that isn't yours (or doesn't
  exist) gives `404 GMAIL_FILE_NOT_FOUND` and **nothing changes**.
- Import / ignore / restore only apply the transitions above. Any other file is left
  as it is and returned unchanged, so a double click can't make two documents.
- The exact JSON shapes are in [`api-contract.md`](api-contract.md) section 13.

## 7. How the Google sign-in works (OAuth)

**OAuth** lets you give Bill Locker a **token** that only allows reading mail
(`gmail.readonly`), without ever giving it your Google password.

```
1. POST /connect (with your login token)
     state     = 32 random bytes     → only its SHA-256 hash is stored
     verifier  = 32 random bytes     (PKCE secret, stored)
     challenge = SHA-256(verifier)   → sent to Google
     nonce     = 32 random bytes     → sent as a cookie; only its hash is stored
2. Browser → Google: client id, scope, state, challenge, access_type=offline, prompt (pick an account)
3. Google → GET /callback?code&state   (the cookie comes along; there is no login token)
     look up the state's hash, and DELETE it (one use, 10 minutes)
     the cookie's hash must equal the stored one, else ?error=expired
     code + verifier → Google's token endpoint → access token + refresh token
     access token → Gmail "profile" → the address
     refresh token → AES-256-GCM → gmail_accounts (new, or a new token for the same address)
```

- **State:** Google's redirect can't carry our login token. The state is a one-time
  random value that says which user started the flow. We store only its hash (like the
  password reset token), so a copy of the database can't be used to finish a sign-in.
- **The state is used exactly once.** It is deleted in one atomic step
  (`consume` counts the rows it deleted), and only the caller whose delete counted
  continues. If two callbacks race, one loses and gets `?error=expired`.
- **PKCE:** Google first gets only the hash of a secret. Finishing needs the secret
  itself, so a stolen one-time code is useless.
- **The browser cookie `gmail_connect`:** `POST /connect` also sets an `HttpOnly`,
  `SameSite=Lax` cookie (path `/api/integrations/gmail`, 10 minutes). It also gets
  `Secure` when the request is HTTPS. The callback only
  works if that cookie matches, and always clears it. **Why:** without it, someone could
  send you *their* Connect link (their state is valid). You'd sign in with Google and
  your mailbox would be attached to **their** account. With the cookie, the callback
  must come from the same browser that clicked Connect, and that is theirs, not yours.
  `HttpOnly` means scripts can't read it; `Lax` still lets the cookie ride along on
  Google's top-level redirect.
- **The callback never shows an error page.** It always answers `302` to the Gmail page
  (`?connected=` or `?error=`), even for a crash (`failed`), or when Gmail import isn't
  configured (`?error=failed`). The page shows a message
  for it. No details from Google are shown or logged, as they could hold secrets.
- **Tokens:** the *access token* works about an hour and is never stored. The *refresh
  token* gets new ones. It is stored encrypted (`TokenCipher`: AES-256-GCM, a new random
  nonce per encryption) with `GMAIL_TOKEN_KEY`, which lives only in `backend/.env`.
  A copy of the database alone can't read anyone's mail. Tokens are never logged and
  never sent to the browser (`ScanJob` / `ImportJob` hide them in `toString`).
- **Several accounts:** the same user can connect more than one address (unique per
  user + address). Connecting an address again (for example after access was removed)
  only replaces its token and queues a scan.
- **Disconnect** revokes the token at Google first (it doesn't matter if that fails, e.g.
  you revoked already), then deletes our copy.

## 8. The scan

`GmailScanWorker.runOnce()` takes one `QUEUED` account:

1. Search Gmail: `after:YYYY/MM/DD has:attachment (invoice OR receipt OR bill OR warranty OR guarantee OR service OR "order confirmation" OR "tax invoice")`. The date comes from the range you chose (the first scan after Connect uses one year).
2. Only the **newest 200** emails of the search are looked at per scan.
3. Emails already in `gmail_emails` are skipped (so scanning again only adds new ones).
4. For each new email, keep the attachments that are bill-like:

   | Rule | Why |
   |---|---|
   | PDF, JPEG, PNG or WebP (by type, or by file extension when the sender says `application/octet-stream`) | Only what the reader can open |
   | Not inline, and has a file name | Inline pictures are part of the email design |
   | At most 10 MB | Same limit as uploads |
   | Images at least 20 KB | Skips logos and signatures; a photographed bill is bigger |

5. An email with no such file is dropped. The rest are classified by `EmailClassifier`
   from the subject, the preview and the file names: junk words (statement, newsletter,
   OTP, offer, sale …) drop the email unless a file name says "invoice"; then
   warranty, service, invoice, receipt, order, else `UNSURE`.
6. `saveScan` stores the emails and files (`NEW`) and sets the account back to `IDLE`.

**Errors** become words you can act on on the account card:

| Google answers | Meaning | Account shows |
|---|---|---|
| 401, `invalid_grant`, or 403 for another reason | You withdrew access (or the 7-day Testing limit passed) | "Gmail access was removed. Connect this account again." |
| 403 with a rate-limit or "API not enabled" reason, 429, 5xx, no network | Temporary | "Gmail could not be reached. Try Scan again later." |
| anything unexpected | | "The scan failed. Please try again." (full error in the backend log) |

## 9. The import

`POST /files/import` only flips the chosen files to `IMPORTING` (a status change you
can see at once). Then `GmailImportWorker`, for each file:

1. Gets a fresh access token, fetches the message, finds the attachment by its **part id**
   (Gmail gives a different attachment id on every fetch, the part id is stable), and downloads it.
2. `DocumentService.createFromBytes(...)`: the same checks as an upload (not empty, ≤ 10 MB,
   really a PDF/JPEG/PNG/WebP by its first bytes), stores the document with
   `source_gmail` and **`read_queued_at` set**.
3. Marks the file `IMPORTED` and links the document.

Failures (shown on the file, with a **Retry**): "This file is no longer in Gmail.",
"This file isn't a PDF or image Bill Locker can read.", "Gmail access was removed…".
A revoked-access import also puts the **account** in `ERROR` ("Gmail access was removed.
Connect this account again."), so the account card tells you to connect it again.

Then `DocumentReadWorker` reads the queued document with Gemini (the same extractor as
task 2) and stores the details, status `EXTRACTED`. If reading fails (no key, quota),
the document stays `UPLOADED` with `read_error`; the document page shows it and you
can press **Read with AI** later, or type the details.

**Why a manual upload is different:** an upload from the Documents page is *not*
queued. The page asks for the read itself (`?read=1`, task 2). Only imports use the queue.

## 10. What you see

- **/gmail:** accounts on top (Connect Gmail, Scan with a range, Disconnect). While a
  scan is queued or running, the page asks again every 3 s (`usePolling`), then shows
  the emails. The tabs show counts of files. Select files and **Import**, **Ignore**
  (To review) or **Restore** (Ignored). A failed file shows its reason and can be
  imported again.
- After Google's redirect the page reads `?connected=` / `?error=`, shows a message
  once, and removes it from the address (a reload doesn't repeat it).
- **/documents:** a Gmail-imported bill shows **Reading…** (icon + label) until the
  background read is done, then **Needs review**. The list refreshes itself every 3 s
  while any document is reading. A "Gmail · address" mark shows where it came from.
- **/documents/:id:** "Reading…" with polling, then the form fills in. If you start
  typing first, your edits are never replaced.

## 11. Set up Google (once, about 10 minutes)

Gmail import is **off** until these settings exist. You can reuse the Google Cloud
project and OAuth client you already made for Bill Locker.

1. Open <https://console.cloud.google.com/> and select the project (or create one,
   e.g. "Bill Locker").
2. **APIs & Services → Library**: search for **Gmail API** and click **Enable**.
3. **Google Auth Platform** (the OAuth consent screen):
   - **Branding:** app name "Bill Locker", your email as the support and contact address.
   - **Audience:** *External*, publishing status *Testing*. Under **Test users**, add
     **every Gmail address you will connect**. An address that isn't listed is blocked by
     Google.
   - **Data access:** the scope `https://www.googleapis.com/auth/gmail.readonly` (only this one).
4. **Clients**: use your *Web application* client (or create one). Under *Authorized
   redirect URIs* it must have exactly
   `http://localhost:8080/api/integrations/gmail/callback`. Copy the **Client ID** and **Client secret**.
5. Put these in `backend/.env` (never commit it; the keys are listed in `.env.example`):
   ```properties
   GOOGLE_CLIENT_ID=…apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=…
   GMAIL_TOKEN_KEY=…            # in Git Bash: openssl rand -base64 32
   FRONTEND_URL=http://localhost:5173   # the address your npm run dev shows
   # GMAIL_REDIRECT_URI=…       # only if you use another redirect address
   ```
6. Restart the backend. The log says `Gmail import is on (redirect URI …)`, or
   `Gmail import is off: …` and which setting is missing.
7. In the app, open **Gmail → Connect Gmail**. Google warns that the app isn't
   verified (normal in *Testing*): continue, and allow read-only access.

Good to know:

- In *Testing* mode Google ends every connection after **7 days**. The account then
  shows "Gmail access was removed…": connect it again.
- Keep `GMAIL_TOKEN_KEY` once it's set. If it changes, stored tokens can't be read:
  scans and imports fail with a "try again" message, and you connect again.
- `GMAIL_TOKEN_KEY` must be 32 bytes in base64 (what `openssl rand -base64 32` prints).
- Without the settings, Connect answers "Gmail import isn't set up on this server yet"
  and nothing else changes.

## 12. Try it

1. Do section 11. Backend + frontend running, signed in → **Gmail**.
2. **Connect Gmail**, allow access. Back on the page: "Connected you@gmail.com. Scanning the last year…".
3. Wait a few seconds: the emails with bills appear under **To review**.
4. Connect a second address: both show under Accounts.
5. Tick a file and **Import**: it says importing, then moves to **Imported**.
6. Open **Documents**: the bill shows **Reading…**, then **Needs review**. Open it, check the details, save.
7. **Ignore** a file: it moves to **Ignored**. **Restore** brings it back.
8. **Scan** again with a longer range: only new emails are added.
9. Delete an imported document, then import its file again from the Imported tab.
10. **Disconnect**: the account and its emails disappear; imported documents stay.

## 13. Tests

`cd backend && ./mvnw test` (needs Docker). Google and Gemini are never called: tests
use `FakeGoogleApi` (`backend/src/test/java/project/bill_locker/FakeGoogleApi.java`,
where a test registers the codes and mailboxes Google would have) and
`FakeDetailExtractor`. `ApiTest` also sets `app.workers.enabled=false`; tests call
`runOnce()` themselves.

| Test class | Checks |
|---|---|
| `gmail/GmailConnectApiTests` | connect URL with state + PKCE, callback (public, redirects), state works once / expired / unknown, denied, cookie set (HttpOnly, Lax) and cleared, missing or another browser's cookie rejected, two accounts, same address updates the token, token stored encrypted, disconnect (also when Google refuses the revoke), other user → 404 |
| `gmail/GmailScanTests` | emails + bill files saved, a second scan adds only new ones, range → `after:` date, inline logos skipped, octet-stream PDF kept by extension, revoked access → message, scan while running → 409, disconnect during a scan, stuck scan re-queued, undecryptable token ends in ERROR, bad range → 400, tabs and bad view |
| `gmail/GmailImportTests` | import creates a queued document and the worker reads it, importing twice makes one document, failures (fake PDF, gone from Gmail, revoked, Gmail down stays importing), ignore / restore / retry, import again after deleting the document, disconnect keeps documents, other user's file → 404 and no change, empty or too many ids → 400 |
| `gmail/GoogleRestApiTests` | the real Google calls against a fake server: URL, token calls, 403 reasons, errors |
| `gmail/EmailClassifierTests` | the keyword rules |
| `gmail/TokenCipherTests` | encrypt / decrypt, a changed bit is noticed |
| `gmail/GmailPropertiesTests` | when the settings count as configured |
| `document/DocumentReadWorkerTests` | the queue: a queued document is read; failure, no key, an unstorable answer or a missing file leave a reason; deleted or saved while queued is left alone; the list shows reading; `createFromBytes` checks like an upload |

The frontend has no tests: `npm run typecheck` and `npm run build`.

## 14. Common problems

| You see | Cause / fix |
|---|---|
| "Gmail import isn't set up on this server yet" | The four settings aren't all valid. The backend log says which is missing or wrong at start-up (`Gmail import is off: …`). Edit `backend/.env`, then restart (`.env` is only read at start-up) |
| Google: "Error 400: redirect_uri_mismatch" | Add `http://localhost:8080/api/integrations/gmail/callback` exactly under the client's *Authorized redirect URIs* |
| Google: "Access blocked … has not completed the Google verification process" | Add that Gmail address under *Audience → Test users*. Every address you connect must be listed |
| After "Allow", the browser can't open `localhost:5173` | `FRONTEND_URL` must be the address `npm run dev` shows (e.g. `http://localhost:5174` when 5173 was busy) |
| Back in the app: "Google could not confirm the connection" | Enable the **Gmail API** in the project (section 11, step 2) |
| "That connection attempt expired. Please try again." | More than 10 minutes passed, the link was used twice, or the sign-in finished in a different browser than the one that clicked Connect |
| "You didn't allow access in Google…" | You pressed Cancel on Google's page. Connect again |
| Account says "Gmail access was removed. Connect this account again." | You withdrew access, or *Testing* mode ended the connection after 7 days. Connect that address again |
| Account says "Gmail could not be reached. Try Scan again later." | Google or the network was temporarily unavailable (also rate limits). Press Scan again |
| A bill you expected isn't in the list | The email is older than the range, beyond the newest 200, has no PDF/image attachment (the bill is in the email text), the image is under 20 KB, or it looked like junk (a "statement" or "offer" without "invoice" in a file name). Scan with a longer range |
| File says "This file is no longer in Gmail." | The email or attachment was deleted after the scan |
| Document stays at "Reading…" | The backend must be running (the worker is part of it). If you changed `.env`, restart. Without `GEMINI_API_KEY`, it ends as `UPLOADED` with a message instead |
| Import does nothing | The files wait for the worker (up to a few seconds). If Gmail is down they stay "importing" and retry on their own |

## 15. Known limits (later tasks)

- **Only attachments.** A bill that exists only in the email's text (no PDF or image)
  isn't offered.
- **No automatic scan.** Scans happen when you connect and when you press **Scan**
  (no daily background scan yet).
- Only the newest 200 matching emails are looked at per scan.
- Classification is by keywords, not AI: it can offer a non-bill or miss an odd one.
- The 7-day limit in Google's *Testing* mode needs a reconnect each week. Removing it
  needs Google's app verification.
- Reading uses the Gemini free tier, one document per 3 s; many imports at once take a while.
- A scan has no notifications yet (the Gmail page only shows results while you look).
