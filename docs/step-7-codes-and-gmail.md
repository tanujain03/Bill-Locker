# Step 7 — Barcodes, QR codes and Gmail import

After this step:

- **Bills are read more exactly.** While reading a bill, Bill Locker also looks for
  barcodes and QR codes:
  - a GST e-invoice QR code gives the invoice number, date and total exactly
  - a serial-number barcode fixes what OCR misread (`CHF2O26` becomes `CHF2026`)
  - a link in a QR code (often the brand's warranty registration page) is shown on the review screen
- **Bills can come from Gmail.** Connect your Gmail (read-only) and a background scan
  makes a shortlist of emails with bills attached. You choose which to import; they
  then go through the same reading and review as uploads.

Still no AI: email sorting uses rules, like the rest of the app for now.

---

## 1. Barcodes and QR codes

```
Background reader (step 4)
  TextReader  → the text (PDF text or OCR)
  CodeReader  → the codes (ZXing)                 ← new
  DetailExtractor.extract(text, codes)
     1. finds the details in the text (rules from steps 4–5)
     2. lets the codes correct them                 ← new
```

**ZXing** ("zebra crossing") is a plain-Java barcode library: one Maven dependency,
no native program (unlike Tesseract). `CodeReader`:

- looks at the photo, or at the first 3 pages of a PDF drawn as pictures (200 dpi)
- finds every code with `GenericMultipleBarcodeReader`: QR and Data Matrix (links,
  e-invoices), Code 128 and Code 39 (serial stickers). Not shop codes (EAN/UPC): they are
  the same on every box, and looking for them in printed text gave false hits (a text
  line "read" as an EAN-8 barcode)
- sorts each one into a kind:

| Kind | What it is | What Bill Locker does with it |
|---|---|---|
| `GST_E_INVOICE` | The signed QR on GST e-invoices: a JWT whose middle part holds `DocNo`, `DocDt`, `TotInvVal` | Invoice number, date and total at 0.95 ("High confidence") |
| `BARCODE` | A one-dimensional barcode, e.g. a serial sticker | If its characters equal the serial (or model) OCR read, apart from look-alikes such as O/0, I/1, S/5, it replaces it at 0.95. If OCR found no serial but the text says "Serial"/"S/N", a single barcode is suggested at 0.7 ("Please verify") |
| `LINK` | A web address in a QR code | Shown on the review screen as a link that opens in a new tab |
| `TEXT` | Anything else in a QR code | Shown as text |

**Safety:** the e-invoice signature isn't checked (that needs the government's public
key), but every value is still only a suggestion until you click Confirm & Save. A QR
link is only made clickable for `http`/`https` addresses (`scanned-codes.ts`), so a
code holding `javascript:…` stays plain text. The full address is shown under the
link, so you see where it goes.

The codes are saved inside the extraction (`documents.extraction.codes`, JSON), so no
new column was needed. A photo with codes but no readable text is now read too.

---

## 2. Gmail import: how connecting works (OAuth 2)

Bill Locker never sees your Google password. Google asks you, then gives Bill Locker
a **token** that only allows reading mail (`gmail.readonly`).

```
App: "Connect Gmail" → POST /api/integrations/gmail/connect
   backend saves a random "state" for you (+ a PKCE secret), answers with Google's address
Browser → Google's consent page → you allow read-only access
Google → GET /api/integrations/gmail/callback?code=…&state=…   (no login token on this request!)
   backend: finds you by the state (one use, 10 minutes)
            trades the code (+ PKCE secret) for tokens at Google
            asks Gmail for your address
            stores the refresh token ENCRYPTED, starts a scan
   → 302 to the app: /gmail?status=connected   (or ?status=error&reason=…)
```

- **Why a state?** Google's redirect can't carry our login token. The state is a
  one-time random value that ties the redirect to the user who started it, and stops
  anyone else from slipping their own Google account into your locker.
- **PKCE:** Google first gets only the hash of a secret. Finishing needs the secret
  itself, so a stolen one-time code is useless.
- **Tokens:** the *access token* works for about an hour and is never stored. The
  *refresh token* gets new access tokens. It is stored encrypted with AES-256-GCM
  (`TokenCipher`), using the key `GMAIL_TOKEN_KEY` that lives only in `backend/.env`.
  A copy of the database alone can't read anyone's mail.
- The callback is the only endpoint without a login token (`SecurityConfig` lets it
  through). It always answers with a redirect to the app, never with Google's error
  details.

## 3. Gmail import: scanning and importing

- **Scan** (`GmailScanWorker`, every 3 seconds; also every morning at 07:30 when
  auto-scan is on):
  - searches `newer_than:1y has:attachment (invoice OR receipt OR bill OR "order confirmation" OR warranty)`
  - fetches only emails it hasn't seen yet
  - keeps the ones with a PDF or photo attachment (up to 10 MB)
  - The slow Gmail calls run **outside** a database transaction, and `GmailService`
    saves the results in short transactions. This is the step 4 pattern again.
- **Sorting** (`EmailClassifier`, rules): "invoice" with an invoice file → 0.95,
  warranty → 0.85, repair or service → 0.8, receipt → 0.75, order or shipping → 0.65,
  statements, offers and OTPs → 0.15. The app offers "Import all" from 0.6 up, and
  sends a "New bills found in Gmail" notification.
- **What's stored:** sender, subject, Gmail's short preview, date and the attachment
  names. **Never the email's full text.**
- **Import:** downloads the chosen emails' attachments. Each is checked like an upload
  (real PDF or image), stored as a document with `source = GMAIL`, and read by the
  background reader. You still review every bill.
- **Disconnect:** gives the token back to Google (revoke), then deletes our copy and
  the shortlist. Imported bills stay in the locker (`documents.gmail_message_id` is set
  to null).

Not built yet: emails whose bill is only in the email text (no attachment). The
contract plans to turn those into PDFs.

## 4. Set up Google (once, about 10 minutes)

You need a Google Cloud project with an OAuth client. Only you can do this, with your
Google account.

1. Open <https://console.cloud.google.com/> and create a project, e.g. "Bill Locker".
2. **APIs & Services → Library**: search for **Gmail API** and click **Enable**.
3. **Google Auth Platform** (the OAuth consent screen):
   - **Branding:** app name "Bill Locker", your email as the support and contact address.
   - **Audience:** *External*, publishing status *Testing*. Under **Test users**, add
     the Gmail address(es) you will connect.
   - **Data access:** add the scope `https://www.googleapis.com/auth/gmail.readonly`.
4. **Clients → Create client**: type *Web application*. Under *Authorized redirect URIs*
   add `http://localhost:8080/api/integrations/gmail/callback`. Create it, then copy
   the **Client ID** and **Client secret**.
5. Put them in `backend/.env` (never commit it; the keys are listed in `.env.example`):
   ```properties
   GOOGLE_CLIENT_ID=…apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=…
   GMAIL_TOKEN_KEY=…            # in Git Bash: openssl rand -base64 32
   FRONTEND_URL=http://localhost:5173   # the address your npm run dev shows
   ```
6. Restart the backend. In the app, open **Gmail Import → Connect Gmail**. Google
   warns that the app isn't verified (normal in *Testing*): continue, and allow
   read-only access.

**If it doesn't work:**

| You see | Fix |
|---|---|
| "Gmail import isn't set up on this server yet" | All **three** settings are needed. The backend log says which is missing or wrong, at start-up: `Gmail import is off: GMAIL_TOKEN_KEY is not set …`. Edit `backend/.env`, then restart the backend (`.env` is only read at start-up) |
| Google: "Error 400: redirect_uri_mismatch" | Add `http://localhost:8080/api/integrations/gmail/callback` exactly under the client's *Authorized redirect URIs* |
| Google: "Access blocked … has not completed the Google verification process" | Add your Gmail address under *Audience → Test users* |
| After "Allow", the browser can't open `localhost:5173` | `FRONTEND_URL` must be the address `npm run dev` shows (e.g. `http://localhost:5174` when 5173 was busy) |
| Back in the app: "Google could not confirm the connection" | Enable the **Gmail API** in the project (step 2) |

Good to know:

- In *Testing* mode Google ends the connection after **7 days**. Connect again then.
- Keep `GMAIL_TOKEN_KEY` once it's set. If it changes, stored tokens can't be read and
  everyone has to connect again.
- Without the three settings, Connect answers "Gmail import isn't set up on this server
  yet" and nothing else changes.

---

## 5. Where everything is

**Backend:** `backend/src/main/java/project/bill_locker/`

| File | What it does |
|---|---|
| `processing/CodeReader` | Finds barcodes and QR codes (ZXing) and sorts them into kinds |
| `processing/DetailExtractor` | `extract(text, codes)`: codes correct the text's details |
| `document/ScannedCode` | One code: format, kind, value, and e-invoice details |
| `document/Document` | New: `source` (UPLOAD/GMAIL) and the email it came from |
| `gmail/GoogleApi`, `GoogleRestApi` | Everything we ask Google; the real one uses `RestClient` |
| `gmail/GmailService` | Connect (state + PKCE), shortlist, import, ignore, disconnect |
| `gmail/GmailScanWorker` | The background scan |
| `gmail/EmailClassifier` | Rules: is this email a bill? |
| `gmail/TokenCipher` | AES-256-GCM for the refresh token |
| `gmail/GmailConnection`, `GmailOAuthState`, `GmailMessage` | The three new tables |
| `gmail/GmailController` | `/api/integrations/gmail…` |

**Frontend:**

| File | Change |
|---|---|
| `src/lib/features.ts` | `gmail` switched on. Only the AI assistant is still hidden |
| `components/documents/ScannedCodes.tsx` | The "Codes on this document" card on the review screen |
| `components/documents/scanned-codes.ts` | `safeWebLink`: only http(s) becomes a link |
| Gmail page and components | Wording without "AI" (rules sort the emails for now) |

## 6. The database

Hibernate added, at start-up:

| Table / column | Holds |
|---|---|
| `gmail_connections` | one per user: Gmail address, encrypted refresh token, auto-scan, scan status, last error |
| `gmail_oauth_states` | sign-ins in progress: state (the id), PKCE verifier, expiry |
| `gmail_messages` | the shortlist: sender, subject, preview, date, attachments (JSON), type, confidence, status |
| `documents.source` | `UPLOAD` or `GMAIL`. Added as `NOT NULL DEFAULT 'UPLOAD'`, so existing documents became `UPLOAD` |
| `documents.gmail_message_id` | the email it came from (SET NULL when the shortlist is deleted) |

**A lesson:** Hibernate's `update` can add a `NOT NULL` column to a table that already
has rows only if the column has a default. `@ColumnDefault("'UPLOAD'")` gives it one.

## 7. Run it

1. **IntelliJ: click "Load Maven Changes"** (the `pom.xml` gained ZXing), then re-run
   **Backend**. Until you do, the restarted app can't find ZXing and stops.
2. Upload a photo of a warranty card or sticker with a barcode or QR code. The review
   screen shows **Codes on this document**.
3. For Gmail, do §4 first.

## 8. Tests

There are 75 backend tests. The new ones:

- **`CodeReaderTests`:**
  - real codes drawn with ZXing's writer and read back: a Code 128 serial sticker,
    a QR link, a GST e-invoice QR inside a PDF
  - a blank photo and a broken file (no codes, no error)
- **`DetailExtractorTests`:** codes correct OCR (the e-invoice wins, O/0 fixed by the
  barcode), and a lone barcode next to "Serial" is only a suggestion.
- **`DocumentProcessingApiTests`:** upload a sticker photo; the API returns the exact serial
  and the code.
- **`GmailApiTests`:**
  - against a fake Google (`FakeGoogleApi`): connect → callback → scan → shortlist,
    with the notification → import → ignore → disconnect
  - a denied or reused sign-in, and nothing to do without a connection
- **`GmailRulesTests`:** token encryption (a changed bit is noticed), the email rules,
  and which attachments can be imported.

The real Google is never called in tests. `ApiTest` swaps in `FakeGoogleApi`
(`@Primary`) and switches the scanner off (`app.gmail.scan-enabled=false`).
