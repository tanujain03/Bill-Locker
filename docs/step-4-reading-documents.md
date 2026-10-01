# Step 4 — Reading documents: the text and the details

After this step, every uploaded bill is **read automatically**. The app gets all the
text out of the file, then finds the details: invoice number, date, total, warranty,
serial number, model, brand, seller and product. You see them on the document page,
each with a confidence badge, together with the full text that was read.

Everything runs on your own PC: no API key, no internet, and your bills never leave
the machine.

---

## 1. The big picture

```
Upload (step 3)                       Background reader (this step), every 2 seconds
───────────────                       ──────────────────────────────────────────────
POST /api/documents/upload            DocumentProcessor.processPendingDocuments()
  → file saved, status UPLOADED         1. take the oldest UPLOADED document → PROCESSING (stage OCR)
  → answer in milliseconds              2. TextReader: get the text (PDF text or OCR)
                                        3. stage EXTRACTION → DetailExtractor finds the details
                                        4. save text + details → PROCESSED
                                           (or FAILED with a message the user can act on)

The app asks GET /api/documents/{id} every 1.5 s while the document is UPLOADED or
PROCESSING, so the progress steps move: Upload → Read → Analyze → Review.
```

Why a background worker? OCR can take a few seconds per page. Doing it inside the
upload request would leave the browser waiting, and a slow upload could time out.
So the upload only stores the file, and the reading happens separately.

---

## 2. Where everything is

**Backend:** `backend/src/main/java/project/bill_locker/`

| File | What it does |
|---|---|
| `processing/DocumentProcessor` | The background worker: `@Scheduled` every 2 s, moves documents through the statuses |
| `processing/TextReader` | Gets the text: PDFBox for PDFs, OCR for photos and scanned PDFs |
| `processing/OcrEngine` | Tesseract OCR (through Tess4J): turns a picture into text |
| `processing/DetailExtractor` | Simple rules (regular expressions) that find the details in the text |
| `processing/UnreadableDocumentException` | "Password-protected PDF", "damaged file" … the message the user sees |
| `document/Document` | New fields: `processingStage`, `errorMessage`, `extractedText`, `extraction` |
| `document/ExtractionResult` | The details found, stored as JSON |
| `document/DocumentService` | Small transactions the worker uses (`startNextProcessing`, `finishProcessing` …) plus `reprocess` |
| `document/DocumentController` | New: `POST /api/documents/{id}/reprocess` ("Try again") |
| `BillLockerApplication` | `@EnableScheduling` switches `@Scheduled` methods on |

**Frontend:**

| File | Change |
|---|---|
| `src/lib/features.ts` | `documentProcessing` added to `BACKEND_FEATURES`, so the progress steps come back |
| `src/components/documents/ExtractedDetails.tsx` | New: read-only list of the details plus the text that was read (replaced by the review screen in step 5) |
| `src/pages/DocumentDetailPage.tsx` | Shows `ExtractedDetails` until products can be saved; "Try again" on failures |

---

## 3. How the text is read (`TextReader`)

| The file | How it's read | Tool |
|---|---|---|
| PDF with text inside (most e-invoices) | The text stored in the PDF is read exactly: fast, no mistakes | **Apache PDFBox** |
| Scanned PDF (the pages are pictures) | Each page is drawn as a 300-dpi image, then read with OCR | PDFBox + **Tesseract** |
| Photo (JPG, PNG, WEBP) | Read with OCR | **Tesseract** |

How it decides a PDF is scanned: if PDFBox finds fewer than 25 characters per page,
the pages must be pictures. Before OCR, photos are turned grey and scaled down to at
most 3000 px, which is faster and reads just as well. `TextReader.clean()` tidies the
result: one kind of line break, single spaces, no empty lines.

**OCR** (optical character recognition) means software that looks at the pixels and
recognises letters. **Tesseract** is the best-known free OCR engine. **Tess4J** lets
Java use it, and it bundles Tesseract and the English language data
(`eng.traineddata`) inside its jar, so there's nothing to install.

---

## 4. How the details are found (`DetailExtractor`)

Each detail has one small rule. A value found next to a clear label is trusted more.
The app turns the confidence into a badge: ≥ 0.85 is "High confidence", 0.60–0.85
is "Please verify", below 0.60 is "Low confidence", and nothing found is "Not found".

**"Label : value" lines come first.** Many bills have a details box such as
`Product : Refrigerator`, `Brand : CoolHome`, `Price : ₹42,999`. A line that *starts*
with one of these labels is the clearest thing on a bill, so it wins (0.85). The other
rules in the table are the fallbacks when there is no such line.

| Detail | Rule (simplified) | Example it finds | Confidence |
|---|---|---|---|
| Invoice number | after "Invoice No / Number / #" (else Bill, Receipt or Order No) | `Invoice No: ME/2026-27/006745` | 0.85 (others 0.7) |
| Purchase date | a date on a line saying "Invoice Date", "Order Date", "Date of Purchase" … | `Invoice Date: 27/09/2026` | 0.85 (else the first date: 0.6) |
| Price | a `Price :` line (also Purchase, Product, Unit or Selling Price); else the biggest amount on a "Grand Total / Amount Payable / Net Amount" line, skipping "Sub Total" and tax lines | `Price : ₹42,999`, `Grand Total ₹8,999.00` | 0.85 (plain "Total": 0.65) |
| Currency | ₹, "Rs" or "INR" before an amount; "$" or "USD"; else a bill that mentions GST is in rupees | INR | 0.8 / 0.7 (GST only: 0.6) |
| Warranty | "2 Years Warranty", "Warranty: 1 Year", "24 months …"; with several periods, the shortest | `1 Year … 10 years on compressor` → 12 months | 0.8 (several: 0.6) |
| Serial number | after "Serial No", "S/N", "IMEI"; must contain a digit | `Serial No: PH9252X77821` | 0.8 |
| Model | after "Model / Model No"; must contain a digit | `Model No: HD9252/90` | 0.8 |
| Brand | a `Brand :` or `Make :` line; else a list of well-known brands, the one mentioned most | `Brand : CoolHome`, `Philips` | 0.85 (list: 0.6, twice or more: 0.75) |
| Product | a `Product :` / `Item Name :` / `Description :` line; else **a guess:** the first line naming the brand, without prices or quantities | `Philips Air Fryer HD9252/90` | 0.85 (guess: 0.5, check it!) |
| Seller | after "Sold By / Seller / Dealer" | `Sold By: Metro Electronics` | 0.85 (name on the next line: 0.65) |
| Document type | words like "Tax Invoice", "Warranty Card", "Job Card" | Invoice | 0.4 to 0.85 |

Dates are read day first (`27/09/2026`), as on Indian bills, unless that's impossible
(`09/27/2026`). Dates in the future and impossible dates (`31/02/2026`) are ignored.

**OCR is messy, so the rules are forgiving.** A photo read by Tesseract often has stray
marks between a label and its value (`InvoiceNo. —-:_ INV-2026-0915-1042`): the rules
skip spaces and the marks `: # . - – — _ =` there. Tesseract also often reads `₹` as
`%`, which is why GST on a bill counts as a sign of rupees. Model and serial numbers
stay at "Please verify" even with a label, because OCR mixes up look-alikes such as
`0`/`O` and `1`/`I`.

**Improved a rule? Click Read again.** Documents keep the details from when they were
read. The **Read again** button on the details card (`POST …/reprocess`) reads the file
again with the current rules.

**What rules can't do well:** decide which line is the product, or read messy layouts.
That's why the product name has low confidence. The next step adds AI, which
understands the text and fills these in far better. The rules remain useful to check
its answers.

---

## 5. Statuses: what the app shows

| Status (stage) | Meaning | In the app |
|---|---|---|
| `UPLOADED` | stored, waiting to be read | "Reading…" progress |
| `PROCESSING` (`OCR`) | getting the text | Read step running |
| `PROCESSING` (`EXTRACTION`) | finding the details | Analyze step running |
| `PROCESSED` | done | "Details found in this document" + a **Read again** button |
| `FAILED` + `errorMessage` | couldn't read it | the reason + a **Try again** button (`POST …/reprocess`) |

If the backend stops halfway through a document, it goes back to `UPLOADED` the next
time the backend starts (`requeueInterrupted`), so nothing gets stuck.

---

## 6. The database

Hibernate added four columns to `documents` on start-up (`ddl-auto=update`). No
reset was needed.

| Column | Type | Holds |
|---|---|---|
| `processing_stage` | varchar(12) | `OCR` / `EXTRACTION` while processing |
| `error_message` | varchar(500) | why reading failed |
| `extracted_text` | text | all the text that was read |
| `extraction` | **jsonb** | the details and their confidence, as JSON |

To see it in pgAdmin, run:
`SELECT file_name, processing_status, extraction FROM documents;`

---

## 7. A real-world lesson: why Tess4J 5.19.0, not 5.20.0

The first version used the newest Tess4J, 5.20.0, and OCR **crashed the whole JVM**
("Invalid memory access"). The JVM's crash report (`hs_err_pid….log`) showed the crash
inside `msvcp140.dll`, the Microsoft C++ runtime. The cause:

- Java 21 ships its own copy of that runtime (version 14.36) and loads it at start-up.
- Tess4J 5.20.0's Tesseract DLL was built with a newer Visual C++ (linker 14.51). DLLs
  built that way need runtime 14.40 or later, or they crash.
- Windows gives every DLL in a process the copy that is already loaded, which is
  Java's older one.

Reading the linker version stored in each release's DLL showed that 5.19.0 (Tesseract
5.5.2) was built with an older compiler (14.29) and works with Java's runtime. A
comment in `pom.xml` explains the pin, so nobody upgrades into the crash again.

---

## 8. Run it

1. **IntelliJ:** click **Load Maven Changes**, the small Maven button top right that
   appears because `pom.xml` gained PDFBox and Tess4J.
2. Stop and re-run **Backend**.
3. The frontend (`npm run dev`) needs no restart.
4. Upload a bill: a PDF invoice works best, but a clear photo works too. Watch
   Upload → Read → Analyze → Review, then click **Review extracted details**.
5. The log shows each document as it's read, for example:
   `Read document … using PDF text in 140 ms` or `… using OCR in 2125 ms`.

To pause reading (for example while debugging), set `app.processing.enabled=false` in
`application.properties`.

## 9. Tests

There are 32 backend tests in total, all run with `./mvnw test`. The new ones:

- **`DetailExtractorTests`:** a shop tax invoice, an online-order invoice, a warranty
  card and the real OCR text of a photographed invoice (with its stray marks), plus
  the date formats.
- **`TextReaderTests`:** real PDFBox and real Tesseract. They cover:
  - a PDF with text
  - a photo of text
  - a scanned PDF
  - password-protected and damaged files
- **`DocumentProcessingApiTests`:** upload, then read, then the details through the
  API. It also covers a blank photo failing with a helpful message, and "Try again".

The scheduled reader is off in tests (`app.processing.enabled=false`), so each test
starts it at a known moment.

## 10. Next

- **AI extraction:** better product names and messy bills.
- **Products and warranties:** done in
  [step 5](step-5-products-and-warranties.md). "Confirm & Save" turns the checked
  details into a product with a tracked warranty, and the review screen (which now
  has the **Read again** button) replaces the read-only view.
