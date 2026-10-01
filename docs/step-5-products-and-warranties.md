# Step 5 — Products, warranties and the dashboard

After this step, a bill becomes something the app **tracks**. On the review screen
you check the details that were read from a bill and click **Confirm & Save**. That
creates a product with its warranty. You can also add products by hand. The app
works out when each warranty ends and whether it is still active, and the
dashboard shows it all at a glance.

No AI is involved: the warranty dates are plain Java, and nothing is saved until
you confirm it.

---

## 1. The big picture

```
Bill uploaded and read (steps 3–4)        You, on the review screen
──────────────────────────────────        ─────────────────────────
document PROCESSED, with suggestions  →   check / correct the details, pick a category
                                          click "Confirm & Save"
                                               │
POST /api/documents/{id}/confirm  ◀────────────┘
  1. create a product (or update the one you picked)
  2. the warranty: months + start date → expiry date (Java, never guessed)
  3. link the document to the product; the warranty remembers it as its source
  4. document → CONFIRMED

Then:  GET /api/products     → My Products (search and filters)
       GET /api/warranties   → Warranties page, soonest expiry first
       GET /api/dashboard/summary → the dashboard
```

**Suggestions vs. data.** What the reader found stays on the document
(`documents.extraction`) as a *suggestion*. Only "Confirm & Save" (or adding a
product by hand) writes to `products` and `warranties`. So a wrongly read value
never becomes data on its own.

---

## 2. Where everything is

**Backend:** `backend/src/main/java/project/bill_locker/`

| File | What it does |
|---|---|
| `product/Category`, `CategoryRepository` | The 9 fixed categories (`kitchen`, `computers` …) |
| `product/DefaultCategories` | Adds the categories when the app starts, if they are missing |
| `product/Product` | The product entity; it always has exactly one `Warranty` |
| `product/ProductInput` | The form's values, with the validation rules (`@NotBlank`, `@PastOrPresent` …) |
| `product/ProductResponse` | A product as the API returns it |
| `product/ProductRepository` | Queries: one product, or a user's products with search and category filter |
| `product/ProductService` | Add, edit, delete, list; `saveFromDocument` for Confirm & Save |
| `product/ProductController`, `CategoryController` | `/api/products…`, `/api/categories` |
| `warranty/Warranty` | The warranty entity: months, start date, expiry date, source document |
| `warranty/WarrantyDates` | **The date rules**: expiry, days left, status |
| `warranty/WarrantyService`, `WarrantyController` | `GET /api/warranties?status=` |
| `dashboard/DashboardService`, `DashboardController` | `GET /api/dashboard/summary` |
| `document/Document` | New: the `product` it belongs to, `attachTo()` and `confirm()` |
| `document/DocumentService` | New: `confirm`; upload with a `productId`; the `productId` filter works |
| `document/ConfirmDocumentRequest`, `ConfirmResult` | Confirm & Save's request and response |
| `common/GlobalExceptionHandler` | Errors inside the nested `product` are reported as `name`, not `product.name` |

**Frontend:** the screens already existed (step 1, on the mock API). This step only switches them on.

| File | Change |
|---|---|
| `src/lib/features.ts` | `products`, `warranties` and `dashboard` added to `BACKEND_FEATURES` |
| `src/pages/DocumentDetailPage.tsx` | Read documents now open the review screen with **Confirm & Save** |
| `src/components/documents/ExtractionReview.tsx` | Wording without "AI" (rules read the bills for now), and **Read again** moved here |
| `src/components/documents/ExtractedDetails.tsx` | Removed: the read-only card from step 4 is replaced by the review screen |
| `DashboardPage`, `ProductsPage`, `ProductDetailsPage` | Gmail, services and "Ask AI" stay hidden until those steps exist |

---

## 3. The warranty rules (`WarrantyDates`)

| Rule | Example |
|---|---|
| expiry = start + months − 1 day | bought 15 Sept 2026 + 24 months → covered **until 14 Sept 2028** |
| days left = expiry − today | negative once it has expired |
| status `EXPIRED` | fewer than 0 days left |
| status `EXPIRING_SOON` | 0 to 30 days left (the last day still counts as covered) |
| status `ACTIVE` | more than 30 days left |
| status `UNKNOWN` | no purchase date, or no warranty period (or 0 months) |

The **expiry date is stored** (so the database could sort by it), but only
`Warranty.setPeriod(months, start)` can change it. That way it always matches.
The **status is never stored**: it changes every day, so it is worked out each time
it is shown. A stored "ACTIVE" would be wrong tomorrow.

The frontend uses the same formula (`utils/date.ts`) to preview the expiry date
while you type, but the backend's answer is the one that counts.

---

## 4. One product, one warranty (JPA relationships)

```
users 1 ── * products 1 ── 1 warranties
                │                │
                │ * documents    └── source_document_id → documents (the bill it came from)
                └─ category_id → categories
```

- `Product.warranty` is a `@OneToOne(mappedBy = "product", cascade = ALL)`. Saving a
  product saves its warranty, and deleting it deletes the warranty.
- `Warranty.product` owns the link: the `warranties.product_id` column, unique
  (`uk_warranties_product`), so a product can't get a second warranty.
- `Document.product` is a `@ManyToOne`: one product can have many bills
  (invoice, warranty card, service receipts).

**Deleting** (the `@OnDelete` rules; the database does the work):

| Delete… | What happens |
|---|---|
| a product | its warranty is deleted (CASCADE). Its documents are **kept**, just unlinked (SET NULL): never lose a bill |
| a document | the warranty stays; it only forgets its source (SET NULL) |
| a user | their products, warranties and documents are deleted (CASCADE) |

The full design ([`database-design.md`](database-design.md)) allows several
warranties per product (standard, extended, per part). That comes later, if needed.

---

## 5. The endpoints

| Method & path | What it does |
|---|---|
| `GET /api/categories` | the 9 categories, in display order |
| `GET /api/products?search=&categoryId=&warrantyStatus=` | your products, newest first |
| `GET /api/products/{id}` | one product (someone else's → 404) |
| `POST /api/products` | add by hand → `201` |
| `PUT /api/products/{id}` | replace all of its details |
| `DELETE /api/products/{id}` | `204`; its documents stay |
| `GET /api/warranties?status=` | soonest expiry first, unknown last |
| `GET /api/dashboard/summary` | everything the dashboard shows |
| `POST /api/documents/{id}/confirm` | Confirm & Save (below) |
| `POST /api/documents/upload` + `productId` | "Add document" on a product's page |
| `GET /api/documents?productId=` | the documents of one product |

**Search** runs in the database: `lower(name) like '%dell%'` on name, brand, model,
seller, serial and invoice number. The **warranty status filter** runs in Java,
because the status depends on today's date.

**Confirm & Save** (`POST /api/documents/{id}/confirm`):

```json
{
  "documentType": "INVOICE",
  "productId": null,
  "product": { "name": "Refrigerator", "brand": "CoolHome", "purchaseDate": "2026-09-15",
               "purchasePrice": 42999, "warrantyMonths": 24, "categoryId": "…" }
}
```

- `productId: null` creates a product; an id updates that product (one of yours).
- It works from `PROCESSED` and from `FAILED`, where you type the details in yourself.
- While the document is still being read, the answer is `409 DOCUMENT_NOT_READY`.
  A second confirm, or "Read again" after saving, gets `409 DOCUMENT_ALREADY_CONFIRMED`.
- The answer is `{ "document": …, "product": … }`, and the app opens the new product.

**Validation:** the rules are annotations on `ProductInput`, for example
`@PastOrPresent(message = "Purchase date cannot be in the future")`. `@Valid` on the
controller checks them, and a broken rule comes back as 400 `VALIDATION_ERROR`, with
the message next to the right field in the form.

---

## 6. The dashboard (`DashboardService`)

It loads your products (with their warranties) and documents **once**, then counts
and adds up in Java:

| Number | How |
|---|---|
| total products / documents | the list sizes |
| to review | documents `PROCESSED` but not confirmed yet |
| total spending | the sum of the purchase prices (assumes one currency, INR) |
| warranty counts | how many are active / expiring soon / expired / unknown |
| spending by category | prices added up per category, biggest first ("Other" without one) |
| upcoming expirations | not expired and ending within 90 days, soonest first |
| recent documents | the 5 newest |

Service records come in a later step, so "upcoming services" is always empty, and
the frontend hides that card for now.

---

## 7. Queries without the "N+1" problem

Listing 50 products and then loading each one's warranty separately would mean 51
queries. That is the classic "N+1" problem. Two fixes are used here:

- **`join fetch`** in `ProductRepository.findForUser` loads products with their
  warranty and category in **one** query. `@EntityGraph` does the same for a single
  product.
- **One grouped query** counts the documents of all products at once:
  `select d.product.id as productId, count(d) as documents … group by d.product.id`.
  Spring Data fills the small `ProductDocumentCount` interface from the `as` names.

---

## 8. The database

Hibernate created three tables and one column on start-up (`ddl-auto=update`).
Your existing documents were untouched, and no reset was needed.

| Table / column | Holds |
|---|---|
| `categories` | name, slug, sort order (filled by `DefaultCategories`) |
| `products` | the purchase details; `user_id`, optional `category_id` |
| `warranties` | `warranty_months`, `start_date`, `expiry_date`, `product_id` (unique), `source_document_id` |
| `documents.product_id` | the product a bill belongs to (empty until linked) |

In pgAdmin:
`SELECT p.name, w.warranty_months, w.expiry_date FROM products p JOIN warranties w ON w.product_id = p.id;`

**A small lesson:** for a `@OneToOne`, Hibernate makes the foreign key unique by
itself, but gives the constraint a random name (`ukpe1qf…`). Declaring
`@UniqueConstraint(name = "uk_warranties_product")` gives it our name. Hibernate's
`update` never removes the old one, so that one was dropped by hand. On a fresh
database only the named one is created.

---

## 9. Run it

1. IntelliJ restarts the backend by itself when the code changes (devtools). If
   it doesn't, re-run **Backend**. No new Maven libraries this time.
2. The frontend (`npm run dev`) needs no restart.
3. Sign in. You now land on the **Dashboard**.
4. Open a read bill under **Documents**, check the details, pick a category and
   click **Confirm & Save**. The new product opens with its warranty.
5. Try **My Products → Add manually**, the search box and the filters, then
   **Warranties** and the **Dashboard**.

## 10. Tests

There are 47 backend tests, all run with `./mvnw test`. The new ones:

- **`WarrantyDatesTests`:** the expiry formula, month ends, unknown warranties and the
  status boundaries (30 days, the last day, expired). Plain Java, no database.
- **`ProductApiTests`:** the categories; add, view, edit and delete; search and filters;
  validation messages per field; someone else's product is a 404; deleting a product
  keeps its documents.
- **`DocumentConfirmApiTests`:** Confirm & Save creates a product and warranty; saving
  into an existing product; field errors from the review form; a document still
  waiting, or already saved, is refused; a failed document saved with typed-in
  details.
- **`DashboardApiTests`:** every number on the dashboard for a known set of products and
  documents, and an empty dashboard for a new account.

`ApiTest` has three new helpers that the tests share: `createProduct`, `categoryId`
and `uploadFile`.

## 11. Next

- **Reminders:** a daily job that warns before a warranty ends (the bell icon), and
  service records with "next service due".
- **AI extraction:** better product names, and a suggested category for each bill.
