# Task 12 — Duplicate bill detection

After this task, Bill Locker notices when a bill is already in the locker, so it is not counted twice
(twice the spending on the Expenses page, two rows for one warranty):

- **The same file again** (exact same bytes): an upload is refused, and the browser opens the bill
  that is already there, with the toast "You already uploaded this file, so here it is."
  A Gmail import of the same attachment (a reply, a reminder email) is linked to the existing bill
  instead of making a copy.
- **The same bill in another file** (a phone photo and the shop's PDF): both bills get an amber
  **"Possible copy"** badge in the lists. The bill's page shows a warning that links to the other one,
  with **Delete this copy** and **Keep both, they're different**.

Nothing is deleted or hidden automatically: the user decides.

## 1. The same file: a fingerprint (`documents.content_hash`)

On every new document the backend stores the **SHA-256** of the file's bytes as 64 hex characters
(`DocumentService.sha256`). Two files with the same hash are the same file; one changed byte gives a
completely different hash. The table stores a short fingerprint, so finding an earlier copy is one
indexed lookup (`idx_documents_user_hash` on `user_id, content_hash`). Nothing has to compare 10 MB
files byte by byte.

| Where | What happens |
|---|---|
| `POST /api/documents/upload` | Same hash for this user → `409 DUPLICATE_FILE` with `documentId` = the earlier bill. Nothing is stored. |
| Gmail import (`createFromBytes`) | Same hash → returns the earlier bill; `GmailImportWorker` marks the Gmail file IMPORTED and links it there. |
| Sample data | Hash stored, never checked (every demo PDF is different anyway). |

Only the **same user's** bills count: two people may upload the same file. Rows from before this task
have no hash (null), so they are never matched by fingerprint, only by the rules below.

`documentId` is a new optional field on the error body (`ApiErrorBody`, `ApiException`). It is left
out of the JSON when null, like `fieldErrors`, so every other error looks the same as before.

## 2. The same bill: rules (`document/DuplicateRules.java`)

Two documents **look like the same bill** when they have the same **purchase date** and either

- the same **bill number** (and not two different shops), or
- the same **shop** and the same **total**.

Names are compared without case, spaces and punctuation, so "INV-77" = "inv 77" and "Croma Ltd." =
"croma ltd". Totals are compared as numbers (1499 = 1499.00). Documents without a date (not read yet)
are never matched. Same date + same shop with another amount (two coffees, different prices) is not
a match.

Why both need the date: every copy of a bill has the same date, so the backend only loads the
**user's documents of that day** (`findByUserIdAndPurchaseDate`) and compares those, not the whole locker.

| Endpoint | Field |
|---|---|
| `GET /api/documents/{id}` (and the answers of save `PUT` and read `POST /extract`) | `possibleDuplicates`: the matching documents (id, file name, shop, number, date, total, status) |
| `GET /api/documents` | `possibleDuplicate: true/false` per row. Checked against **all** the user's dated bills, so a search that finds only one of the two still marks it |
| `POST /api/documents/{id}/not-duplicate` | "Keep both": sets `documents.not_duplicate`; returns the document (now without warnings). Another user's document → 404 |

**Keep both** marks the document, and a marked document is never a match on either side, so the
warning disappears on both bills. (A Boolean column for the same reason as `sample`: Hibernate can add
it to a table that already has rows.)

## 3. Frontend

| File | Job |
|---|---|
| `lib/api.ts` | `ApiError.documentId` (from the error body) |
| `lib/documents.ts` | `DuplicateMatch`, `possibleDuplicates`, `possibleDuplicate`, `markNotDuplicate` |
| `lib/useUploadBill.ts` | On `DUPLICATE_FILE`: open the earlier bill + info toast (top bar, phone bar, drag and drop all use it) |
| `components/documents/DuplicateWarning.tsx` | The amber warning (links, "Delete this copy", "Keep both") and the `DuplicateBadge` |
| `components/documents/DocumentList.tsx` | The "Possible copy" badge (Documents and Bills & receipts pages) |
| `pages/DocumentPage.tsx` | Shows the warning; Delete uses the normal delete (with confirm); Keep both only changes the warning, so edits on screen stay |

Like every status colour, amber comes with an icon (two pages) and a label.

## 4. Tests

| Test | Checks |
|---|---|
| `document/DocumentDuplicateApiTests` | Same file → 409 + earlier id; another user may upload it; same number + date (written differently) and same shop + date + total match; other amount / day / shop don't; another user's bill never matches; the list flags (also through a search); Keep both clears both sides; Keep both on someone else's bill → 404 |
| `gmail/GmailImportTests.sameAttachmentInTwoEmailsBecomesOneDocument` | Two emails with the same PDF → one document, both Gmail files point at it |
| `sample/SampleDataApiTests` | No two demo bills look alike |

`DocumentApiTestBase.pdfBytes()` now returns a slightly different PDF every call. Many tests upload
several bills for one user, and identical bytes would now be refused.

## 5. Not done (on purpose)

- No unique constraint on the hash: two uploads of the same file in the same split second can both get in.
  They would then show as "Possible copy" (same details), so the user still sees them.
- Duplicates are not left out of the Expenses totals automatically. The warning asks the user, because
  two real purchases can look the same.
