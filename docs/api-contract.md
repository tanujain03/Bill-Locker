# Bill Locker — REST API contract

This is the full REST API Bill Locker is heading towards. The backend and the
frontend are rebuilt from scratch on the `restart` branch, one task at a time;
each task implements a part of this contract.

### Implemented so far (restart branch, tasks 1–2)

| Endpoint | Notes |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` | Complete, except login rate limiting |
| `POST /api/auth/forgot-password`, `POST /api/auth/reset-password` | Complete. The reset link is emailed over SMTP (`MAIL_*` settings); without them it is written to the backend log |
| `/api/documents/...` (upload, extract, list + search, get, save, download, delete) | Complete, see section 8. Reading uses Google Gemini (`GEMINI_API_KEY`) |

Everything else is not built yet. How tasks 1 and 2 work is explained in
[`task-1-auth.md`](task-1-auth.md) and [`task-2-documents.md`](task-2-documents.md).

---

## 1. Conventions

| Topic | Rule |
|---|---|
| Base path | `/api` (the frontend calls `/api/...`; Vite dev proxy / nginx forward it to the backend) |
| Format | JSON (`application/json`), except file upload (multipart) and download (binary) |
| Auth | `Authorization: Bearer <JWT>` on every endpoint except `POST /api/auth/register`, `/login`, `/forgot-password` and `/reset-password` |
| User scoping | The current user comes **only** from the JWT. Never accept a `userId` from the client. Every query filters by it (`WHERE user_id = :currentUser`), including vector search. |
| Ownership | Accessing another user's product/document/record returns **404** (not 403) so existence isn't leaked |
| IDs | Opaque strings (UUIDs recommended) |
| Dates | Calendar dates `YYYY-MM-DD` (`ISODate`); timestamps ISO-8601 UTC (`ISODateTime`) |
| Money | `number` in major units (e.g. `62990` or `1499.5`) + `currency` (ISO 4217, default `INR`) |
| Missing values | `null` — never `""`, never a guess. The AI's `NOT_FOUND` is normalised to `null`. |
| Success bodies | The resource itself (no envelope). Lists are plain arrays. `DELETE` → `204 No Content`. |

### Error format (all non-2xx responses)

```json
{
  "success": false,
  "code": "DOCUMENT_NOT_FOUND",
  "message": "The requested document was not found.",
  "fieldErrors": { "name": "Product name is required" }
}
```

`fieldErrors` is only present for `VALIDATION_ERROR`; keys match request field
names so the UI can show them next to the inputs. Messages must be safe to show
to users (no stack traces, SQL or internal details).

| Status | Typical `code` |
|---|---|
| 400 | `VALIDATION_ERROR`, `INVALID_RESET_TOKEN`, `FILE_REQUIRED`, `INVALID_UPLOAD` |
| 401 | `UNAUTHORIZED` (missing/expired token), `INVALID_CREDENTIALS` (login) |
| 404 | `PRODUCT_NOT_FOUND`, `DOCUMENT_NOT_FOUND`, `SERVICE_RECORD_NOT_FOUND`, `NOTIFICATION_NOT_FOUND`, `GMAIL_MESSAGE_NOT_FOUND` |
| 409 | `EMAIL_ALREADY_REGISTERED`, `DOCUMENT_NOT_READY`, `DOCUMENT_ALREADY_CONFIRMED`, `GMAIL_NOT_CONNECTED` |
| 413 | `FILE_TOO_LARGE` |
| 415 | `UNSUPPORTED_FILE_TYPE` |
| 429 | `RATE_LIMITED` |
| 500 | `SERVER_ERROR` |

The frontend treats any `401` on an authenticated call as "session expired":
it clears the token and sends the user to `/login`.

---

## 2. Enums

| Enum | Values |
|---|---|
| `WarrantyStatus` | `ACTIVE`, `EXPIRING_SOON`, `EXPIRED`, `UNKNOWN` |
| `DocumentType` | `INVOICE`, `WARRANTY_CARD`, `RECEIPT`, `OTHER` (built in task 2) |
| `DocumentStatus` | `UPLOADED`, `EXTRACTED`, `SAVED` (built in task 2) |
| `ProcessingStage` | `OCR`, `EXTRACTION`, `INDEXING` (optional detail while `PROCESSING`) |
| `DocumentSource` | `UPLOAD`, `GMAIL` |
| `ServiceType` | `ROUTINE_MAINTENANCE`, `REPAIR`, `INSTALLATION`, `INSPECTION`, `OTHER` |
| `NotificationType` | `WARRANTY_EXPIRING`, `WARRANTY_EXPIRED`, `SERVICE_DUE`, `DOCUMENT_PROCESSED`, `GMAIL_BILLS_FOUND` |
| `GmailSyncStatus` | `IDLE`, `SYNCING`, `ERROR` |
| `GmailMessageStatus` | `NEW`, `IMPORTED`, `IGNORED` |

---

## 3. Authentication

### `POST /api/auth/register`
Request `{ "name": "Asha Verma", "email": "asha@example.com", "password": "Str0ngPass" }`
- name 2–80 chars; valid email (stored lower-cased, unique); password 8–128 chars with ≥1 letter and ≥1 digit, stored hashed (BCrypt).
- `201` → `AuthResponse`. Duplicate email → `409 EMAIL_ALREADY_REGISTERED` with `fieldErrors.email`.

### `POST /api/auth/login`
Request `{ "email": "...", "password": "..." }` → `200 AuthResponse`.
Wrong email **or** password → `401 INVALID_CREDENTIALS` "Incorrect email or password." (same message for both — no account enumeration). Rate-limit this endpoint.

```json
// AuthResponse
{
  "token": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-29T10:00:00Z",
  "user": { "id": "…", "name": "Asha Verma", "email": "asha@example.com", "createdAt": "2026-08-14T09:12:00Z" }
}
```
The JWT should carry `sub` (user id) and `exp`; the frontend reads `exp` to drop expired tokens early.

### `GET /api/auth/me` → `User`
### `PUT /api/users/me` `{ "name": "New Name" }` → `User` (not built yet)

### `POST /api/auth/forgot-password`
Request `{ "email": "asha@example.com" }` → always `200 { "message": "If an account exists for this email, we've sent a link to reset the password." }`,
whether or not the email has an account (no account enumeration). If it has one, any earlier
link stops working and a new one-time link `<FRONTEND_URL>/reset-password?token=<token>` is sent
(by email; written to the backend log when no mail account is set up). The link expires after 30 minutes. Only a SHA-256 hash
of the token is stored (`password_reset_tokens`).

### `POST /api/auth/reset-password`
Request `{ "token": "<from the link>", "password": "N3wPassword" }` → `200 { "message": "Your password has been changed. You can sign in now." }`.
- password: same rules as register (`fieldErrors.password` if broken).
- Unknown, used or expired token → `400 INVALID_RESET_TOKEN`. A token works once.

---

## 4. Categories

### `GET /api/categories` → `Category[]`
`{ "id": "…", "name": "Computers & Accessories", "slug": "computers" }`

Seeded slugs (the UI maps them to icons): `mobile-phones`, `computers`,
`tv-entertainment`, `audio`, `home-appliances`, `kitchen`, `furniture`, `vehicles`, `other`.

---

## 5. Products

### `Product`
```json
{
  "id": "…",
  "categoryId": "…", "categoryName": "Computers & Accessories", "categorySlug": "computers",
  "name": "Dell Inspiron 15 3530 Laptop",
  "brand": "Dell", "model": "Inspiron 15 3530", "serialNumber": "DL3530-7XK2P91",
  "purchaseDate": "2025-10-24", "purchasePrice": 62990, "currency": "INR",
  "seller": "Metro Electronics, Bengaluru", "invoiceNumber": "ME/2025-26/004812",
  "warranty": {
    "id": "…", "warrantyMonths": 12, "startDate": "2025-10-24", "expiryDate": "2026-10-23",
    "status": "EXPIRING_SOON", "daysRemaining": 24
  },
  "nextServiceDate": null,
  "documentCount": 1,
  "createdAt": "…", "updatedAt": "…"
}
```
`warranty` may be `null` (treated as `UNKNOWN`). `nextServiceDate` = next service
date of the product's most recent service record.

### `ProductInput` (create / update)
```json
{
  "name": "string (required, ≤120)",
  "categoryId": "string | null",
  "brand": "string | null (≤80)", "model": "string | null (≤80)", "serialNumber": "string | null (≤80)",
  "purchaseDate": "YYYY-MM-DD | null (not in the future)",
  "purchasePrice": "number ≥ 0 | null",
  "currency": "ISO-4217, default INR",
  "seller": "string | null (≤120)", "invoiceNumber": "string | null (≤80)",
  "warrantyMonths": "integer 0–240 | null"
}
```
`warrantyMonths` creates/updates the product's warranty with `startDate = purchaseDate`.

| Method & path | Result |
|---|---|
| `GET /api/products?search=&categoryId=&warrantyStatus=` | `Product[]`, newest first. `search` matches name, brand, model, seller, serial, invoice number (case-insensitive). |
| `GET /api/products/{id}` | `Product` |
| `POST /api/products` | `201 Product` |
| `PUT /api/products/{id}` | `Product` (full replace of the input fields) |
| `DELETE /api/products/{id}` | `204`. Deletes the product, its warranty, service records and product notifications. **Documents are kept and unlinked** (`productId = null`) — never lose a bill. |

---

## 6. Warranties

Warranty maths is deterministic Java code — never the LLM:

- `expiryDate = startDate.plusMonths(warrantyMonths).minusDays(1)` (e.g. 2026-09-15 + 24 months → **2028-09-14**)
- `daysRemaining = expiryDate − today` (whole days, negative once expired)
- `status`: `UNKNOWN` if months or start date is missing (or months = 0); `EXPIRED` if `daysRemaining < 0`;
  `EXPIRING_SOON` if `0 ≤ daysRemaining ≤ 30`; otherwise `ACTIVE`.

### `GET /api/warranties?status=` → `Warranty[]` (soonest expiry first, unknown last)
`Warranty` = the product's warranty summary plus `productId`, `productName`,
`productBrand`, `categoryName`, `categorySlug`, `sourceDocumentId`.

---

## 7. Dashboard

### `GET /api/dashboard/summary`
```json
{
  "totalProducts": 9,
  "totalDocuments": 13,
  "documentsToReview": 1,
  "totalSpending": 395348,
  "currency": "INR",
  "warranties": { "total": 9, "active": 4, "expiringSoon": 2, "expired": 2, "unknown": 1 },
  "spendingByCategory": [{ "categoryName": "Home Appliances", "categorySlug": "home-appliances", "amount": 163470 }],
  "upcomingExpirations": [ /* Warranty[], 0–90 days left, soonest first */ ],
  "upcomingServices": [ /* ServiceRecord[], next service within 60 days or overdue, one per product */ ],
  "recentDocuments": [ /* DocumentSummary[], 5 newest */ ]
}
```

---

## 8. Documents

Built in task 2 (details in [`task-2-documents.md`](task-2-documents.md)). The
fields follow `invoice_warranty_fields.csv`: bill-level fields on the document,
product + warranty fields per item.

`DocumentStatus`: `UPLOADED` (stored, not read) → `EXTRACTED` (AI filled it, needs
review) → `SAVED` (user saved it). `DocumentType` (task 2): `INVOICE`,
`WARRANTY_CARD`, `RECEIPT`, `OTHER`.

| Method & path | Result |
|---|---|
| `POST /api/documents/upload` (multipart part `file`) | `201 DocumentDetail`. PDF/JPEG/PNG/WebP by magic bytes, ≤ 10 MB, file name stripped of paths. `400 FILE_EMPTY`, `400 FILE_TOO_LARGE`, `400 INVALID_FILE_TYPE` |
| `POST /api/documents/{id}/extract` | Reads the file with AI (synchronous), replaces details + items, status `EXTRACTED` → `DocumentDetail`. `503 AI_NOT_CONFIGURED`, `502 EXTRACTION_FAILED` (document unchanged) |
| `GET /api/documents?q=&type=&status=` | `DocumentSummary[]`, newest first. `q`: file name, document number, seller, product name (case-insensitive) |
| `GET /api/documents/{id}` | `DocumentDetail` |
| `PUT /api/documents/{id}` | Saves reviewed details; `items` replaces the list; status `SAVED` → `DocumentDetail`. `400 VALIDATION_ERROR` + `fieldErrors` (e.g. `items[0].warrantyEndDate`) |
| `GET /api/documents/{id}/download` | the file bytes, `Content-Type` = stored type, `Content-Disposition: inline; filename="…"` (fetched with the Bearer header, previewed via an object URL) |
| `DELETE /api/documents/{id}` | `204` (file and items too) |

```json
// DocumentDetail
{
  "id": "…", "fileName": "croma.pdf", "contentType": "application/pdf", "sizeBytes": 73402,
  "status": "EXTRACTED", "documentUrl": "/api/documents/…/download",
  "documentType": "INVOICE", "documentNumber": "INV-1029",
  "sellerName": "Croma", "sellerAddress": "…", "sellerContact": "…",
  "buyerName": "…", "buyerAddress": "…", "buyerEmail": "…",
  "purchaseDate": "2026-01-10", "taxAmount": 228.66, "totalAmount": 1499.00,
  "items": [{
    "productName": "Phone", "modelNumber": "M-1", "serialNumber": "SN-1", "unitPrice": 1199,
    "warrantyPeriodMonths": 12, "warrantyStartDate": "2026-01-10", "warrantyEndDate": "2027-01-09",
    "warrantyProvider": "Samsung"
  }],
  "createdAt": "…", "updatedAt": "…"
}
```
`DocumentSummary`: `id, fileName, contentType, sizeBytes, status, documentType,
documentNumber, sellerName, purchaseDate, totalAmount, itemCount, firstProductName, createdAt`.
`PUT` body = the details part of `DocumentDetail` (`documentType` … `items`), all optional.

Later tasks: linking documents to products/warranties, Gmail as a source, AI search
over documents.

---

## 9. Service records

`ServiceRecord`: `id, productId, productName, serviceDate, serviceType, serviceCenter,
cost, currency (of the product), nextServiceDate, notes, createdAt, updatedAt`.

Input: `{ productId, serviceDate (≤ today), serviceType, serviceCenter?, cost? (≥0), nextServiceDate? (> serviceDate), notes? (≤500) }`

| Method & path | Result |
|---|---|
| `GET /api/service-records?productId=` | `ServiceRecord[]`, newest service first |
| `POST /api/service-records` | `201 ServiceRecord` |
| `PUT /api/service-records/{id}` | `ServiceRecord` |
| `DELETE /api/service-records/{id}` | `204` |

---

## 10. Notifications

`AppNotification`: `id, productId, documentId, type, title, message, scheduledAt, read, createdAt`.

A daily scheduled job (idempotent — de-duplicate per warranty/expiry date and per service/due date) creates:
- `WARRANTY_EXPIRING` when a warranty has ≤ 30 days left — e.g. "Your Dell laptop warranty expires in 30 days."
- `WARRANTY_EXPIRED` when it has expired (within the last 60 days)
- `SERVICE_DUE` when a product's next service is ≤ 7 days away (or recently overdue)

Event-driven: `DOCUMENT_PROCESSED` (extraction ready) and `GMAIL_BILLS_FOUND` (scan found bills).

| Method & path | Result |
|---|---|
| `GET /api/notifications` | `AppNotification[]`, newest first |
| `GET /api/notifications/unread-count` | `{ "count": 4 }` (polled every 30 s) |
| `PATCH /api/notifications/{id}/read` | `AppNotification` |
| `POST /api/notifications/read-all` | `204` |

Click targets used by the UI: `DOCUMENT_PROCESSED` → `/documents/{documentId}`,
`GMAIL_BILLS_FOUND` → `/gmail`, others → `/products/{productId}`.

---

## 11. AI assistant — `POST /api/ai/chat`

Request `{ "message": "Is my laptop still under warranty?", "sessionId": "…" | null }` (message 1–1000 chars)

Response:
```json
{
  "sessionId": "chat_…",
  "message": {
    "id": "msg_…",
    "role": "ASSISTANT",
    "content": "Yes — your **Dell Inspiron 15 3530 Laptop** is under warranty until **23 Oct 2026** (24 days left).",
    "references": [
      { "type": "PRODUCT", "id": "…", "title": "Dell Inspiron 15 3530 Laptop", "subtitle": "Expires in 24 days" },
      { "type": "DOCUMENT", "id": "…", "title": "Dell_Inspiron_Invoice.pdf", "subtitle": "Invoice · Dell Inspiron 15 3530 Laptop" }
    ],
    "createdAt": "…"
  }
}
```
- Build context from the user's structured data (products, warranties, services)
  **and** pgvector search over `document_chunks WHERE user_id = :currentUser`.
- `content` supports paragraphs, `- ` bullet lists, `1. ` lists and `**bold**` only (rendered safely, never as HTML).
- If the answer isn't in the user's data, say so. Never invent values. Return the
  products/documents actually used as `references`.

## 12. Natural-language search — `POST /api/ai/search`

Request `{ "query": "Show products whose warranty expires within 90 days" }`

The LLM only converts the query into this structure; the backend validates it and
runs a parameterised query (no LLM-generated SQL):

```json
{
  "query": "Show products whose warranty expires within 90 days",
  "filters": { "daysUntilExpiry": 90, "sortBy": "WARRANTY_EXPIRY", "sortDirection": "ASC" },
  "explanation": "Showing products whose warranty expires within the next 90 days, soonest expiry first.",
  "results": [ /* Product[] */ ]
}
```
`SearchFilters` (all optional): `text`, `categorySlug`, `brand`, `seller`,
`warrantyStatus`, `daysUntilExpiry` (not yet expired and ≤ N days left),
`purchasedAfter`, `purchasedBefore`, `minPrice`, `maxPrice`,
`sortBy` (`PURCHASE_DATE` | `PRICE` | `WARRANTY_EXPIRY`), `sortDirection` (`ASC` | `DESC`), `limit`.
The UI shows the interpreted filters as chips, so users can see how their
question was understood.

---

## 13. Gmail import

Lets users import bills from their inbox instead of uploading files. Google tokens
never reach the browser.

### OAuth flow
```
UI  ── POST /api/integrations/gmail/connect ──▶ backend: create OAuth `state` bound to the user
UI  ◀─ { authorizationUrl } ─────────────────── (Google consent URL, scope gmail.readonly,
                                                 access_type=offline, prompt=consent)
UI  ── window.location = authorizationUrl ────▶ Google consent screen
Google ── GET /api/integrations/gmail/callback?code&state ──▶ backend: verify state, exchange
                                                 code, store refresh token encrypted, start a scan
backend ── 302 ──▶ {FRONTEND_URL}/gmail?status=connected   (or ?status=error&reason=…)
```
The callback is a browser redirect without a JWT — the user is resolved from the
server-side `state`. The frontend only follows `authorizationUrl` if it is on
`accounts.google.com` or the app's own origin.

### Scan
Search recent mail (e.g. `newer_than:1y (invoice OR receipt OR bill OR "order confirmation" OR warranty)`),
let the AI classify each candidate (`detectedType`, `confidence`), and store the
shortlist. Nothing is imported automatically.

### Endpoints
| Method & path | Result |
|---|---|
| `GET /api/integrations/gmail` | `GmailConnection` — `{ connected, email, connectedAt, lastSyncedAt, autoSync, syncStatus, lastError }` (polled every 1.5 s while `SYNCING`) |
| `POST /api/integrations/gmail/connect` | `{ "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?..." }` |
| `GET /api/integrations/gmail/callback` | OAuth redirect target (backend only) → 302 to the frontend |
| `POST /api/integrations/gmail/sync` | `GmailConnection` with `syncStatus: "SYNCING"`; `409 GMAIL_NOT_CONNECTED` |
| `PUT /api/integrations/gmail/settings` | `{ "autoSync": true }` → `GmailConnection` (daily background scan) |
| `GET /api/integrations/gmail/messages` | `GmailMessage[]` |
| `POST /api/integrations/gmail/import` | `{ "messageIds": ["…"] }` → `{ "documents": DocumentSummary[] }` — each attachment (or the email body rendered to PDF when there's no attachment) enters the normal document pipeline with `source: "GMAIL"` |
| `POST /api/integrations/gmail/messages/{id}/ignore` | `GmailMessage` with `status: "IGNORED"` |
| `DELETE /api/integrations/gmail` | `204` — revoke the Google token, delete stored tokens and the shortlist (imported documents stay) |

```json
// GmailMessage
{
  "id": "…",
  "fromName": "Amazon.in", "fromEmail": "auto-confirm@amazon.in",
  "subject": "Your Amazon.in order of Sony WH-1000XM5 Wireless Headphones",
  "snippet": "The tax invoice for order #408-… is attached.",
  "receivedAt": "…",
  "attachments": [{ "fileName": "Invoice_408-5561234-9912.pdf", "mimeType": "application/pdf", "size": 84213 }],
  "detectedType": "INVOICE",
  "confidence": 0.97,
  "status": "NEW",
  "documentIds": []
}
```
The UI offers "Import all" only for emails with `confidence ≥ 0.6`.

---

## 14. Mock-only endpoints

Served by the mock API, not required from the backend:

- `POST /api/__mock/reset` — restore the seeded demo data (Settings → "Reset demo data").
- `GET /api/health` — `{ "status": "UP", "mode": "mock" }` (the backend can expose Actuator health instead).
