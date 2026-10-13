git # Task 10 — Warranty claim kit

After this task, when a product breaks, one click gives everything needed to claim its
warranty:

- **A "Claim" link on every row of the Warranties page**, and a **"Claim kit" button on every
  product of a saved bill** (hidden while the bill has unsaved edits, because the kit reads
  the saved bill).
- **The claim kit page** (`/warranties/claim/:documentId/:position`):
  1. a banner with how the warranty stands today (covered until …, ends soon, ended, or unknown);
  2. **Describe the problem**: a text box, quick choices ("It does not charge" …) and the date it started;
  3. **Your claim email**: subject and message written from the bill's facts and the problem,
     updated as you type, with **Open in email app** and **Copy email**;
  4. **Where to send it**: the brand's customer care (web search), service centres near me
     (Google Maps), the brand's warranty page if known (task 5), and the shop's phone;
  5. **Claim checklist**: which facts a service centre asks for are present, which are missing,
     and a link to add them on the bill;
  6. **Download invoice** and **Print claim sheet** (the menus, chat and tab bar are hidden on paper).

**Bill Locker never sends anything.** The email opens in the user's own email app
(`mailto:`), where they add the brand's address, attach the invoice and press send. No brand
form is filled or submitted (the project rule from task 5).

No new tables, no new libraries.

---

## 1. The big picture

```
Browser                                          Backend
WarrantiesPage ─ "Claim" ─┐
DocumentPage ─ "Claim kit"┴▶ ClaimKitPage ── GET /api/documents/{id}/items/{position}/claim-kit
                                              ▶ ClaimKitController → ClaimKitService
                                                  ├─ DocumentRepository.findByIdAndUserId (another user's bill → 404)
                                                  ├─ WarrantyRules: end date, status, days left (same rules as task 4)
                                                  └─ links + missing facts
                              claimEmail() in lib/claims.ts writes the email from the kit + the problem
```

**Why the facts come from the backend but the email is written in the browser:** the facts and
the warranty status follow the same rules as the Warranties page and are tested in Java. The
email is only words around those facts, and it has to change on every key press while the user
describes the problem, so it is built in the browser (no request per letter).

## 2. The kit (`warranty/ClaimKit.java`, `ClaimKitService.java`)

| Part | Where it comes from |
|---|---|
| Product | product name, brand, model, serial number, unit price (the bill's product line) |
| Purchase | shop name, phone, address, invoice number, purchase date, buyer, file name (the bill) |
| Warranty today | provider, start, end (printed, or start + months − 1 day), days left, status (`WarrantyRules`) |
| `supportSearchUrl` | `https://www.google.com/search?q=<brand> customer care warranty claim` |
| `serviceCentreUrl` | `https://www.google.com/maps/search/?api=1&query=<brand> authorised service centre near me` |
| `registrationUrl` | the brand's registration page saved in task 5, if any |
| `missing` | of: product name, model number, serial number, purchase date, invoice number, warranty end date |

Without a brand, the links search for the product name instead. An expired warranty still gets
a kit (the banner says so; paid repairs and goodwill repairs are still possible). The product is
named by its bill and its position on it (0, 1, 2…); a position the bill doesn't have is
`404 ITEM_NOT_FOUND`, another user's bill a 404 as everywhere else.

`WarrantyView` (the Warranties page rows) now carries `position`, so each row can link to its kit.

## 3. The email (`frontend/src/lib/claims.ts`)

`claimEmail(kit, { problem, since, name, email })` returns a subject and a message. The subject
is "Warranty claim – product (model) – Serial …"; the message greets the brand (or the warranty
provider), lists only the facts the bill has (never "Serial number: null"), then the problem
(or the placeholder "[Describe the problem here]"), the start date if given, a closing line
about the attached invoice, and the signed-in user's name and email (from the account, changeable in Settings).
`mailtoLink()` encodes it into a `mailto:` link without a recipient.

The message is shown in a `<pre>` as plain text: nothing read from a bill is ever rendered as HTML.

## 4. Files

| File | Job |
|---|---|
| `backend/…/warranty/ClaimKit.java` | The kit's shape |
| `backend/…/warranty/ClaimKitService.java` | Gathers the facts, applies `WarrantyRules`, builds the links and the missing list |
| `backend/…/warranty/ClaimKitController.java` | `GET /api/documents/{id}/items/{position}/claim-kit` |
| `backend/…/warranty/WarrantyView.java`, `WarrantyService.java` | `position` on each warranty row |
| `frontend/src/lib/claims.ts` | Types, `getClaimKit`, `claimKitLink`, common problems, `claimEmail`, `mailtoLink` |
| `frontend/src/pages/ClaimKitPage.tsx` | The page (banner, four cards, download, print) |
| `frontend/src/pages/WarrantiesPage.tsx` | "Claim" on each row |
| `frontend/src/components/documents/DetailsForm.tsx`, `pages/DocumentPage.tsx` | "Claim kit" on saved products |
| `frontend/src/components/AppLayout.tsx` | `print:hidden` on the sidebar, top bar, tab bar, chat and reminders |
| `frontend/src/App.tsx` | The route |

## 5. Tests

`backend/src/test/java/project/bill_locker/warranty/ClaimKitApiTests.java`: a complete kit
(facts, 365 days left, links with the brand, nothing missing); a product with only start date +
months and no brand (worked-out end date, links with the product name, model and serial missing);
an expired warranty still gets a kit; another user's product and a missing position are 404; the
warranty rows carry their position.
