# Task 5 — Warranty registration links

Many bills carry a **warranty registration link** or a **"scan to register" QR code**.
After this task, each product on a bill shows that link under **Warranty registration**,
clickable, opening the brand's registration page in a **new tab**:

1. **Link printed on the bill** → the AI copies it while reading the bill.
2. **QR code on the bill** → the backend decodes the QR code exactly (it's only processing:
   the user never scans anything) and the AI tells which product the link belongs to.
3. **Neither (edge case)** → the AI detects the **brand** from the product name. The page
   asks: *"It looks like a Noise product. Is that right?"*
   - **Yes, open Noise's registration page** → the backend finds Noise's official warranty
     registration page (Gemini + Google Search, checked that it opens) and it opens in a new tab.
     The link is then kept on the product.
   - **No, change the brand** → the cursor goes to the **Brand** field; correct it, then confirm.
   - Nothing reliable found → a Google search for "<brand> warranty registration" opens instead.

Nothing is searched without the user's confirmation, and Bill Locker only **opens** the
brand's page: it never fills in or submits the brand's form (a project rule).

New: 3 columns on `document_items`, 2 libraries (ZXing, PDFBox), 1 endpoint.

---

## 1. The big picture

```
Reading a bill (Read with AI, or the Gmail background read)
  DocumentService.read(file)
    ├─ QrCodeReader.links(file)                → ["https://brand.example/warranty/register?…"]  (ZXing, exact)
    ├─ DetailExtractor.extract(file, qrLinks)  → items with brand + warrantyRegistrationUrl    (Gemini)
    └─ withRegistrationSources(...)            → QR_CODE if the link came from a QR code, else DOCUMENT

No link on the bill — the user clicks "Yes, open Noise's registration page"
  browser opens an empty tab at once ("Finding…")   ← during the click, so it isn't blocked as a pop-up
  POST /api/documents/{id}/registration-page {position, brand: "Noise"}
    DocumentService.findRegistrationPage
      ├─ RegistrationFinder.find("Noise", "Noise Buds VS104")
      │    GeminiRegistrationFinder: Gemini + google_search tool → links in its answer,
      │    the brand's own domain first → LinkChecker: https, public address, really opens
      ├─ found → WEB_SEARCH, else a Google search link → SEARCH
      └─ stored on the product (next time it's simply a link)
  the waiting tab goes to the answer's url
```

---

## 2. Backend, file by file

| File | What it does |
|---|---|
| `document/RegistrationSource.java` | `DOCUMENT`, `QR_CODE`, `WEB_SEARCH`, `SEARCH` (and `USER`): where a link came from. |
| `document/DocumentItem.java` | New columns `brand` (100), `registration_url` (1000), `registration_source`. Hibernate `update` adds them; old rows stay empty. |
| `document/DocumentItemView.java` | The three new fields. A second constructor without them keeps older code and tests unchanged. |
| `document/SaveDocumentRequest.java` | `registrationUrl` must be `http(s)://…`: the page makes it clickable, so `javascript:` must never get in. |
| `document/registration/QrCodeReader.java` | ZXing decodes the QR codes in a photo, or in the first 3 pages of a PDF (drawn as images by PDFBox). Keeps web links only (not UPI or GST e-invoice codes). Never fails a read. |
| `document/ai/GeminiDetailExtractor.java` | The prompt and schema ask for `brand` (null for rides, food, utilities) and `warrantyRegistrationUrl`; the QR links are added to the prompt when there are any. |
| `document/ai/GeminiAnswerParser.java` | `webLink()`: only `http(s)` links survive (`www.lg.com/…` gets `https://`). |
| `document/DocumentService.java` | `read()` = QR codes → AI → rules. `withRegistrationSources()`: link in the QR list → `QR_CODE`, else `DOCUMENT`; a QR link with "warranty"/"regist" in it still reaches products the AI left empty. `findRegistrationPage()`: the confirmed brand's page, searched outside any transaction (it is slow). |
| `document/RegistrationPageRequest.java`, `RegistrationPage.java` | The endpoint's body `{position, brand}` and answer `{url, source}`. |
| `document/registration/RegistrationFinder.java` | The interface; tests use `FakeRegistrationFinder` (never online). |
| `document/registration/GeminiRegistrationFinder.java` | The same Gemini REST call as reading, plus `"tools": [{"google_search": {}}]`: Gemini answers from real search results. `candidates()` takes the links from the answer and puts the brand's own domain first (`gonoise.com` for Noise). |
| `document/registration/LinkChecker.java` | Opens the link before we trust it. **Safety:** the address comes from outside, so only `https` and only public internet addresses (never `localhost`, `10.x`, `192.168.x`, `169.254.x`…), checked again on every redirect. Otherwise a crafted link could make our server call internal services. |
| `document/registration/RegistrationFinderConfig.java` | With `GEMINI_API_KEY`: the Gemini finder. Without: finds nothing → Google search links. |
| `warranty/WarrantyView.java` | `registrationUrl` for the Warranties page. |

---

## 3. Frontend

| File | What it does |
|---|---|
| `lib/documents.ts` | Types and `findRegistrationPage(id, position, brand)`. |
| `lib/document-form.ts` | `brand` is a form field; `registrationUrl` / `registrationSource` travel with the form (not editable boxes). |
| `components/documents/RegistrationLink.tsx` | Under each product's Warranty: the clickable link (new tab, `noopener`) + where it came from; or, without one, the brand question with **Yes, open … registration page** / **No, change the brand**. |
| `components/documents/DetailsForm.tsx` | **Brand** field per product (filled by the AI); shows `RegistrationLink`. |
| `pages/DocumentPage.tsx` | `openRegistrationPage()`: opens the tab during the click, asks the backend, sends the tab to the page; keeps the link on the form. |
| `pages/WarrantiesPage.tsx` | **Register ↗** next to **Open bill** on each row with a link. |

---

## 4. API

`POST /api/documents/{id}/registration-page` `{ "position": 0, "brand": "Noise" }` → `{ "url": "…", "source": "WEB_SEARCH" | "SEARCH" }`

- `400 VALIDATION_ERROR` (`fieldErrors.brand`) when the brand is empty.
- `404 ITEM_NOT_FOUND` when there is no product at that position (e.g. added but not saved); another user's document → `404`.
- The link is stored on that product (when it's still the same product).

Items now also have `brand`, `registrationUrl`, `registrationSource`.

---

## 5. Try it

1. **Load the new libraries:** IntelliJ → Maven tool window → **Load Maven Changes** (the
   `pom.xml` got ZXing and PDFBox), then run **Backend** again.
2. **Bills read before this task have no link stored:** open one and press **Read again**.
3. **Printed link / QR code:** a bill like the demo invoice (registration URL + QR code) →
   under the product's Warranty: the link, "Printed on the bill" or "From the QR code on the
   bill". Click it → the page opens in a new tab.
4. **No link, no QR:** e.g. a Noise earbuds bill → "It looks like a **Noise** product. Is that
   right?" → **Yes** → a new tab opens on Noise's registration page. **No, change the brand**
   → fix the Brand field → **Yes**.
5. Without `GEMINI_API_KEY`, step 4 opens a Google search instead.
6. `/warranties`: **Register ↗** on rows with a link.

Tests: `RegistrationLinkApiTests` (printed, QR, confirmed brand found and kept, the user's
corrected brand, Google fallback, empty brand / missing product, `javascript:` refused, other
users), `RegistrationPartsTests` (QR reader, `LinkChecker` refusing private addresses, picking
the brand's domain), `GeminiAnswerParserTests` (brand, links).

## 6. Limits

- The web search uses the Gemini free tier's Google Search quota. When it's used up or busy,
  a Google search opens instead.
- A found page is the best web match, checked to open, but not guaranteed official.
- WebP photos aren't scanned for QR codes (Java can't read WebP without another library).
