# Task 13 — Product pages

After this task, every product on a bill has its own page (`/products/:documentId/:position`)
with everything about it in one place:

- **Header**: name, brand · model · category, warranty status, price, and the actions
  **Claim kit** (task 10), **Register warranty** (when the brand's page is known, task 5) and **Open bill**.
- **Missing details** banner (amber) when the bill lacks a model number, serial number, price,
  purchase date or warranty end date, linking to the bill to add them.
- **Warranty**: a bar from the warranty's start to its end with today marked ("88 days left of a
  12-month warranty from Samsung India"); colour, icon and words agree (never colour alone).
- **Owning it**: how long you've owned it ("9 months, since 10 Jan 2026") and what it has cost per
  month so far (the price spread over the whole months owned, at least one month).
- **Details**: brand, model, serial number, price, shop, purchase date, invoice number, warranty
  provider; a missing one says "Not on the bill" in amber.
- **Documents**: the bill the product is on, plus **related documents** found by the same serial
  number (e.g. a warranty card uploaded separately) or, failing that, the same model number. Each
  can be opened or downloaded.

**How to get there:** the product name on the Warranties page (table and phone cards), a row of the
dashboard's "Expiring soon", the **Product page** button on each saved product of a bill (next to
Claim kit; hidden while the bill has unsaved edits), and the product name on its claim kit.

No new tables, no new libraries.

## 1. Backend (`product` package)

```
ProductPage ── GET /api/documents/{id}/items/{position}/product ─▶ ProductController → ProductService
                                                      ├─ DocumentRepository.findByIdAndUserId (another user's bill → 404)
                                                      ├─ WarrantyRules: end date, status, days left (same as the Warranties page)
                                                      └─ DocumentRepository.findMentioning: related documents
```

| Field | Rule |
|---|---|
| `warrantyEndDate`, `daysLeft`, `status` | `WarrantyRules` (printed end, or start + months − 1 day) |
| `ownedDays` | days from the purchase date to today (never below 0); null without a purchase date |
| `costPerMonth` | price ÷ whole months since the purchase, at least 1 (bought this month = the full price); null without price or date |
| `related` | the user's **other** documents with a product of the same serial number or model number, compared without case or surrounding spaces; `matchedBy` = "serial number" when the serial matches, else "model number". No serial and no model → none (nothing to match on). Only the user's own documents. |
| `missing` | of: model number, serial number, price, purchase date, warranty end date |

A position the bill doesn't have is `404 ITEM_NOT_FOUND`, like the claim kit.

`findMentioning` is one query (`select distinct d … join d.items i where … lower(trim(i.serialNumber)) = :serial or …`);
an empty serial or model is passed as `""`, which the query skips, so a blank never matches every
blank.

## 2. Frontend

| File | Job |
|---|---|
| `lib/products.ts` | Types, `getProduct`, `productLink`, `ownedFor` ("9 months", "1 year 2 months") |
| `pages/ProductPage.tsx` | The page (header + actions, missing banner, warranty timeline, owning it, details, documents) |
| `pages/WarrantiesPage.tsx` | Product names link to the page |
| `components/dashboard/ExpiringList.tsx` | Rows open the product page (were: the bill) |
| `components/documents/DetailsForm.tsx`, `pages/DocumentPage.tsx` | "Product page" next to "Claim kit" (`savedLinks` replaces `claimLink`) |
| `pages/ClaimKitPage.tsx` | The product name links back to its page |
| `App.tsx` | The route |

## 3. Tests

`backend/src/test/java/project/bill_locker/product/ProductApiTests.java` ("today" = 7 Oct 2026):
everything about one product (worked-out end date, 91 days left, owned 273 days, ₹8,888.78 a month,
nothing missing); related documents by serial number (written in another case, with spaces) and by
model number, but not an unrelated bill or another user's bill; a product bought this month costs
its full price per month and lists its missing facts; another user's product and a missing position
are 404.
