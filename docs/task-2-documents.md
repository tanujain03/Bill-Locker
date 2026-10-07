# Task 2 — Documents: upload, read with AI, review, save

You upload an invoice, warranty card or receipt (PDF or photo). Bill Locker keeps
the file in the database, asks **Google Gemini** (free tier) to read it, and shows
the details next to the file. You check them, fix anything wrong, and save. Every
field has a **copy** button, each product has **Copy product**, and **Copy all**
copies the whole document. Documents can be searched, opened, edited, read again,
downloaded and deleted.

The fields are the ones in `invoice_warranty_fields.csv`. This guide says **what
happens where**, so you can follow a click to the database and back.

## 1. The big picture

```
Browser (React, :5173)                 Spring Boot (:8080)                         PostgreSQL / Gemini
──────────────────────                 ───────────────────                         ───────────────────
DocumentsPage: drop a file
  POST /api/documents/upload ────────▶ DocumentController.upload
  (multipart, part "file")             DocumentService.upload
                                         empty? > 10 MB? → 400
                                         FileType.detect(first bytes) → PDF/JPEG/PNG/WebP, else 400
                                         save Document (status UPLOADED) ───────▶ documents
                                         save DocumentFile (the bytes) ─────────▶ document_files
  ◀──────────── 201 DocumentDetail
navigate to /documents/{id}?read=1

DocumentPage (sees ?read=1)
  POST /api/documents/{id}/extract ──▶ DocumentService.extract
                                         1. load the file (short transaction) ◀── document_files
                                         2. DetailExtractor.extract(bytes) ─────▶ Gemini generateContent
                                            (no transaction: may take ~5-20 s)  ◀── JSON with our fields
                                            GeminiAnswerParser cleans it up
                                            work out missing warranty end dates
                                         3. replace details + items,
                                            status EXTRACTED (short transaction) ─▶ documents, document_items
  ◀──────────── 200 DocumentDetail
form filled → user checks / fixes / copies

  PUT /api/documents/{id} ───────────▶ DocumentService.save
  (all details + all products)           @Valid rules → 400 VALIDATION_ERROR + fieldErrors
                                         warranty end before start → 400
                                         replace details + items, status SAVED ─▶ documents, document_items
  ◀──────────── 200 DocumentDetail
```

Statuses: **UPLOADED** (file stored, not read) → **EXTRACTED** (AI filled it, "Needs
review") → **SAVED** (you saved it). Saving again later (editing) keeps it SAVED.

Why is reading a separate request instead of part of the upload? The upload is
then never lost: if Gemini is down, out of free quota or has no key, the file is
already stored, the page shows a message, and you can type the details yourself or
press **Read again** later.

## 2. Files: backend (`backend/src/main/java/project/bill_locker/`)

| File | Job |
|---|---|
| `document/Document.java` | Entity → table `documents`: the file's name/type/size, `status`, and the bill-level CSV fields. `replaceDetails(...)` swaps in new details + products |
| `document/DocumentItem.java` | Entity → table `document_items`: one product on the bill (name, model, serial, unit price, warranty) |
| `document/DocumentFile.java` | Entity → table `document_files`: the file's bytes (`bytea`), same id as the document |
| `document/DocumentType.java`, `DocumentStatus.java` | The two enums (`INVOICE`/`WARRANTY_CARD`/`RECEIPT`/`OTHER`; `UPLOADED`/`EXTRACTED`/`SAVED`) |
| `document/FileType.java` | Recognises PDF/JPEG/PNG/WebP from the file's first bytes ("magic number") |
| `document/DocumentRepository.java` | `findByIdAndUserId` (someone else's → empty → 404) and the `search` query for the list |
| `document/DocumentFileRepository.java` | Loads/saves the bytes |
| `document/DocumentService.java` | **The rules**: upload checks, list/search, save (+ warranty date check), extract (+ warranty end date), download, delete |
| `document/DocumentController.java` | HTTP ↔ Java: the 7 endpoints below |
| `document/DocumentDetail.java`, `DocumentSummary.java`, `DocumentItemView.java` | What the browser receives (records, never entities) |
| `document/DocumentDetails.java` | The details of a bill: what the AI returns and what `save` stores |
| `document/SaveDocumentRequest.java` | The body of `PUT`, with the validation rules as annotations |
| `document/DownloadedFile.java` | Name + type + bytes on their way to the browser |
| `document/ai/DetailExtractor.java` | Interface: "give me the details of this file". Swap AI providers by writing one new class |
| `document/ai/GeminiDetailExtractor.java` | The real one: calls Gemini's REST API (section 5) |
| `document/ai/GeminiAnswerParser.java` | Turns Gemini's JSON into `DocumentDetails`, forgiving messy values |
| `document/ai/ExtractionException.java` | "Reading failed": code `AI_NOT_CONFIGURED` or `EXTRACTION_FAILED` + a message for the user |
| `document/ai/DetailExtractorConfig.java` | At start-up: key set → Gemini; no key → an extractor that always says "not configured" |
| `common/GlobalExceptionHandler.java` | Now also turns "upload over 10 MB" (stopped by Spring before our code) into `400 FILE_TOO_LARGE` |

## 3. Files: frontend (`frontend/src/`)

| File | Job |
|---|---|
| `lib/api.ts` | Still the only place that calls the backend. New: `FormData` bodies (uploads) and `fetchBlob` (downloads) |
| `lib/documents.ts` | Types matching the backend records, labels (`FIELD_LABELS`, `ITEM_LABELS`), one function per endpoint, `formatAmount` |
| `lib/document-form.ts` | The form holds text; `toForm`/`fromForm` convert to/from the backend's shape. `detailsToText`/`itemToText` build what **Copy** puts on the clipboard. `warrantyEndFor` previews the end date the backend will fill in |
| `components/AppHeader.tsx` | Top bar for signed-in pages: logo, Home, Documents, Sign out |
| `components/documents/StatusBadge.tsx` | Status as icon + label + colour |
| `components/documents/CopyButton.tsx` | Copies text with `navigator.clipboard.writeText`; tick icon for 1.5 s |
| `components/documents/DocumentPreview.tsx` | Downloads the file with the token, shows it from a temporary `blob:` URL (PDF in an `<iframe>`, photo in an `<img>`) |
| `components/documents/DetailsForm.tsx` | The review form: cards for Document, Seller, Buyer, Purchase, then Products (each with a Warranty part; Add / Remove / Copy product) |
| `pages/DocumentsPage.tsx` | `/documents`: drop zone, search box, type + status filters, the list |
| `pages/DocumentPage.tsx` | `/documents/:id`: preview + form. Top: Read again, Copy all, Download, Delete. End of the form: the save bar |
| `pages/HomePage.tsx` | Now uses `AppHeader` and links to Documents |

## 4. Database

Hibernate creates the three tables from the entities (`ddl-auto=update`).

| Table | Rows | Columns |
|---|---|---|
| `documents` | one per bill | `id`, `user_id`, `file_name`, `content_type`, `size_bytes`, `status`, `document_type`, `document_number`, `seller_name`, `seller_address`, `seller_contact`, `buyer_name`, `buyer_address`, `buyer_email`, `purchase_date`, `tax_amount`, `total_amount`, `created_at`, `updated_at` |
| `document_items` | one per product on a bill | `id`, `document_id`, `position`, `product_name`, `model_number`, `serial_number`, `unit_price`, `warranty_period_months`, `warranty_start_date`, `warranty_end_date`, `warranty_provider`, `created_at`, `updated_at` |
| `document_files` | one per bill | `document_id`, `data` (the bytes) |

- **Why products are a separate table:** one invoice can list a phone and a charger,
  each with its own serial number and warranty.
- **Why the bytes are a separate table:** the list and the document page never load
  whole files; only the preview/download and the AI read `document_files`.
- **`document_url`** from the CSV isn't stored: it is always
  `/api/documents/{id}/download` (sent as `documentUrl`). It needs the login token.
- Deleting a user deletes their documents; deleting a document deletes its products
  and file (`ON DELETE CASCADE`).

## 5. Endpoints

All need `Authorization: Bearer <token>`. Another user's document → `404 DOCUMENT_NOT_FOUND`.

| Endpoint | Answer |
|---|---|
| `POST /api/documents/upload` (multipart, part `file`) | `201 DocumentDetail`, status `UPLOADED`. `400 FILE_EMPTY` / `FILE_TOO_LARGE` / `INVALID_FILE_TYPE` |
| `POST /api/documents/{id}/extract` | `200 DocumentDetail`, status `EXTRACTED`. `503 AI_NOT_CONFIGURED`, `502 EXTRACTION_FAILED` (document unchanged) |
| `GET /api/documents?q=&type=&status=` | `200 DocumentSummary[]`, newest first. `q` searches file name, document number, seller and product names |
| `GET /api/documents/{id}` | `200 DocumentDetail` |
| `PUT /api/documents/{id}` | `200 DocumentDetail`, status `SAVED`. `400 VALIDATION_ERROR` + `fieldErrors` |
| `GET /api/documents/{id}/download` | the file, `Content-Disposition: inline; filename="…"` |
| `DELETE /api/documents/{id}` | `204` |

`PUT` body (every field optional; `items` is the full list, it replaces the old one):

```json
{
  "documentType": "INVOICE", "documentNumber": "INV-1029",
  "sellerName": "Croma", "sellerAddress": "Mumbai", "sellerContact": "1800-123",
  "buyerName": "Asha", "buyerAddress": "Pune", "buyerEmail": "asha@example.com",
  "purchaseDate": "2026-01-10", "taxAmount": 228.66, "totalAmount": 1499.00,
  "items": [
    { "productName": "Phone", "modelNumber": "M-1", "serialNumber": "SN-1", "unitPrice": 1199,
      "warrantyPeriodMonths": 12, "warrantyStartDate": "2026-01-10", "warrantyEndDate": "2027-01-09",
      "warrantyProvider": "Samsung" }
  ]
}
```

Rules: texts ≤ 500 characters, `buyerEmail` must look like an email, amounts ≥ 0 with at
most 10 digits before the point and 2 after,
warranty 0–600 months, warranty end not before its start, at most 50 products.
Errors point at the field, e.g. `fieldErrors["items[0].warrantyEndDate"]`.

`DocumentDetail` is that same shape plus `id`, `fileName`, `contentType`,
`sizeBytes`, `status`, `documentUrl`, `createdAt`, `updatedAt`.

## 6. How the Gemini call works

`GeminiDetailExtractor` sends **one** HTTP request:

```
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent
x-goog-api-key: <GEMINI_API_KEY>          ← a header, so the key never appears in logged URLs
{
  "contents": [{ "parts": [
     { "text": "<our instructions: copy values exactly, null when not printed, one item per product…>" },
     { "inline_data": { "mime_type": "application/pdf", "data": "<the file, base64>" } }
  ]}],
  "generationConfig": {
     "response_mime_type": "application/json",
     "response_schema": { "type": "OBJECT", "properties": { "documentNumber": …, "items": { "type": "ARRAY", … } } },
     "temperature": 0
  }
}
```

- **`inline_data`**: Gemini reads PDFs and photos itself. There's no OCR step in our code.
- **`response_schema`**: forces the answer to be JSON with exactly our field names,
  instead of a chatty paragraph.
- **`temperature: 0`**: no creativity. The same bill gives the same answer.
- The answer arrives as text inside `candidates[0].content.parts[0].text`.
  `GeminiAnswerParser` reads it and is **forgiving**: `"₹1,499.00"` → `1499.00`,
  `"24 months"` → `24`, an unknown type → `OTHER`, a date not in `YYYY-MM-DD` or a
  number it can't be sure of (`"1.2.3"`) → empty, for you to fill in. Text longer than
  500 characters is cut; an amount with more than 10 digits (a misreading) → empty. Only an
  answer that isn't JSON at all fails the read.
- If a product has a warranty start and length but no end date, the service works
  it out, after an AI read and (since task 4) on save: start 10 Jan 2026 + 12 months →
  ends 9 Jan 2027.
- Errors become messages you can act on: HTTP 429 → "free Gemini limit is used
  up…", 400 → "couldn't open this file", 401/403/404 → "check GEMINI_API_KEY and
  GEMINI_MODEL", 503 → "Gemini is busy", anything else or
  a timeout (60 s) → "try again". The details are logged at WARN; the key never is.
- **Backup models:** `GEMINI_MODEL` may list several models, comma-separated. They are
  tried in order, and the next one only when a model answers 503 (busy) or 429 (out of
  quota), because those are per model. A wrong key or a broken file fails on every
  model, so those stop at once. If all models are busy, the last model's message is shown.

**Why the AI call is outside a database transaction:** a transaction holds a
database connection until it ends. Waiting 20 s for Gemini inside one would block
that connection the whole time. So `extract` does: short transaction (read the
file) → Gemini (no transaction) → short transaction (store the answer). If Gemini
fails, step 3 never runs, so the document stays exactly as it was.

## 7. Setting it up (free key)

1. Open https://aistudio.google.com/apikey, sign in with a Google account, and click
   **Create API key**. No card is needed.
2. In `backend/.env` add:
   ```
   GEMINI_API_KEY=your-key-here
   ```
3. Restart the backend (IntelliJ **Backend**). The log says
   `Documents are read with Gemini model(s) [gemini-3.5-flash]`.

- No key? The log warns once, upload still works, and **Read with AI** shows "AI
  reading is not set up…". You type the details yourself.
- **Privacy:** on the free tier Google may use what you send to improve its
  products. Use sample or test bills you're happy to share.
- **Other model:** add `GEMINI_MODEL=<model id>` (see
  https://ai.google.dev/gemini-api/docs/models). Any model that reads PDFs/images works.
  Backups: `GEMINI_MODEL=gemini-3.5-flash,gemini-2.5-flash` tries the second when the first is busy.
- **Free limits:** Google limits requests per minute and per day (see AI Studio →
  Rate limits). Each **Read** is one request.

## 8. Copy

- The copy icon in every field copies that value.
- **Copy product** copies one product as lines:
  ```
  Product 1
  Product name: Phone
  Serial number: SN-1
  Warranty (months): 12
  ```
- **Copy all** copies the bill details, then every product, separated by blank lines.
- Copy uses what is **on screen**, including edits you haven't saved. Empty fields
  are skipped. The download link isn't included because it only works with your login.

## 9. The review page (UX)

- **Save is at the end of the form**, where you finish checking. The save bar sticks
  to the bottom of the window while you scroll, so it's always in reach, and rests
  after the last product. **Ctrl+S** (⌘S on a Mac) saves too.
- The bar says where you are: *Check the details, then save* (AI read it) →
  *Unsaved changes* (with **Discard**) → *Saving…* → *Saved*. A failed save shows the
  reason in the bar, the message under each wrong field, and puts the cursor in the
  first one.
- Unsaved edits are protected: **All documents** asks before leaving, and so does the
  browser on reload or closing the tab (`beforeunload`).
- The file preview stays in view on the left (`sticky`), so you can compare while
  scrolling the fields.
- An empty **Warranty end** says which date it will get on save ("Will be
  11 Sept 2027…"), the same rule as the backend: start + months − 1 day.
- Removing a product that has details asks first. **Delete** (the whole document) is
  set apart from the other buttons, in red.

## 10. Try it

1. Backend + frontend running, signed in → **Documents**.
2. Drop a PDF or photo of a bill. You land on its page and see "Reading your
   document with AI…".
3. Check the fields, fix one: the bar says *Unsaved changes*. Press **Save details** (or Ctrl+S) → *Saved*.
4. Try a copy button and paste somewhere.
5. Back on **Documents**: search for the seller or a product; filter by type/status.
6. **Download** and **Delete** on the document page.

## 11. Tests

`cd backend && ./mvnw test` (needs Docker). Gemini is never called: tests use
`FakeDetailExtractor` (`backend/src/test/java/project/bill_locker/FakeDetailExtractor.java`), which returns a
fixed invoice or fails on request.

| Test class | Checks |
|---|---|
| `document/DocumentApiTests` | upload ok / fake PDF / empty / too big / no token, download bytes + headers, delete, other user → 404 |
| `document/DocumentListApiTests` | only your documents, newest first, status filter, file-name search |
| `document/DocumentSaveApiTests` | save + reload, items replaced, validation errors, warranty dates, search by seller/product/number/type, other user → 404 |
| `document/DocumentExtractApiTests` | AI fills details + warranty end date, failure leaves the document unchanged, no key → 503, reading twice doesn't duplicate products |
| `document/ai/GeminiAnswerParserTests` | full answer, messy money/dates/types, unreadable numbers, missing items, not-JSON |
| `document/ai/GeminiDetailExtractorTests` | the exact request (key header, base64 file, schema) against a fake server; 429 / 403 / 503 / "no answer" messages |
| `common/GlobalExceptionHandlerTests` | an upload over Spring's 10 MB limit → `400 FILE_TOO_LARGE` |

The frontend has no tests yet: `npm run typecheck` and `npm run build`.

## 12. Common problems

| You see | Cause / fix |
|---|---|
| "AI reading is not set up…" | No `GEMINI_API_KEY` in `backend/.env`, or the backend wasn't restarted |
| "Gemini refused the request…" | Wrong key, or `GEMINI_MODEL` names a model that doesn't exist. The backend log shows Google's exact answer |
| "The free Gemini limit is used up…" | Too many reads in a minute/day. Wait and press **Read again** |
| "Gemini is busy right now…" | Google's model is overloaded ("high demand", temporary). Try again in a minute, or add a backup model to `GEMINI_MODEL` (e.g. another Flash model from the models page) and restart the backend |
| "Gemini couldn't open this file…" | Google could not read the file (damaged or password-protected PDF). Try another scan, or type the details in |
| "Gemini could not read this document right now…" (log: `Could not reach Gemini`) | No answer within 60 s, usually because the model is overloaded (it can hang ~80 s before saying "high demand"). Try again later, or switch `GEMINI_MODEL` |
| "Files can be at most 10 MB." | Make the scan smaller (lower resolution or fewer pages) |
| "Please upload a PDF, JPG, PNG or WebP file." | The file isn't really one of those (we check its bytes, not its name). HEIC photos from iPhones: export as JPEG |
| Some fields empty after reading | Not printed, or not readable. Type them in; that's what the review step is for |

## 13. Known limits (later tasks)

Products, warranties and the dashboard don't use these details yet. There's no
currency or quantity field (not in the CSV). Search uses simple `LIKE` matching. A
list of many documents loads each one's products separately (fine for a personal
locker).
