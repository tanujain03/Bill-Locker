# Task 15 — Service and maintenance records

Products on saved bills now keep repairs, routine services, replacement parts and warranty claims.
Service history follows the permanent product ID, so editing or reordering a bill cannot attach a
repair to a different product. No new libraries or migration scripts.

## 1. Backend (`servicerecord` package)

Controller → `ServiceRecordService` → `ServiceRecordRepository`. JSON requests and responses are
records; entities never leave the service. `product_service_records` is created by Hibernate ddl-auto.
The older build may already have a populated `service_records` table with incompatible columns.
That legacy table is left untouched; no reset, destructive alteration or migration is needed.
The owner comes only from the JWT. Another user's document, record or attachment returns 404.

| Endpoint | Job |
|---|---|
| `POST /api/documents/{id}/items/{position}/services` | Create on a SAVED bill. The UI also sends `?productId=UUID` to reject a stale position. |
| `GET /api/documents/{id}/items/{position}/services` | That product's service history, newest service date first, then created time and ID. |
| `GET /api/services?type=&q=&due=` | Search title, notes, provider, product and claim number; filter type or due status. |
| `PUT /api/services/{id}` | Edit details; cannot move a record to another product or user. |
| `DELETE /api/services/{id}` | Delete one owned record. |

Fields: type (REPAIR / ROUTINE_SERVICE / PART_REPLACED / WARRANTY_CLAIM / OTHER), title, notes
(`description`), serviceDate, providerName, providerContact, nullable cost, coveredByWarranty,
claimNumber, claimStatus (OPEN / RESOLVED / REJECTED), nextDueDate, repeatMonths, and optional
attachmentDocumentId. Attachments link to existing owned documents, using their normal protected
preview/download endpoints. No second file store. Claim fields are cleared for other record types.

Validation uses inline `fieldErrors`: title required (200 chars), notes up to 4000, serviceDate
required and no later than `LocalDate.now(clock)`, cost nonnegative with at most two decimal places,
repeatMonths 1–60, and nextDueDate on or after serviceDate. A bill not yet SAVED returns
409 DOCUMENT_NOT_SAVED for adding, listing product history or editing. Record deletion remains
available to the owner. Removing a product or its bill cascades to its records; deleting an
attachment only clears the attachment link. Removing sample data also removes sample services.

## 2. Dates, costs and reminders

`ServiceRules` is a pure function with today supplied by the application's Clock:

| Next due | Status |
|---|---|
| absent | NONE |
| before today | OVERDUE |
| today through today + 30 days inclusive | DUE_SOON |
| later | UPCOMING |

An interval computes `serviceDate.plusMonths(repeatMonths)` (31 January + one month clamps to
February's last day). It takes precedence over an explicit next date. The interval belongs to
that record; it does not silently create or charge for future services.

Only the newest **scheduled** record per product drives current reminders (`reminderActive`). A
newer repair with no schedule does not erase an existing maintenance date. Historical scheduled
dates stay visible in the timeline, labelled as previous schedules. Due filters select current
schedules; NONE selects unscheduled records. Updating/deleting the current schedule recomputes
which remaining schedule applies. There is no stored or scheduled status that can go stale.

Product responses add serviceHistory, totalServiceCost, lastServiceDate and nextServiceDue.
Cost per month = (purchase price + recorded service costs) ÷ whole months owned (minimum one).
Unknown service costs add nothing; an unknown purchase price/date still leaves cost per month
unknown. Existing expense totals continue to describe purchase bills, avoiding double counting.
Claim kits add previousServices. Dashboard servicesDue contains up to five current overdue/due-soon
records, earliest due first. The bell links service reminders back to their products.

## 3. Assistant and sample data

`ai_service_records` is a security-barrier view using the same app.user_id + app.user_sig check
as the existing views. The AI role gets SELECT on the view only, not on product_service_records or file
bytes. Its schema description explains current schedules and joins through product_id/document_id.
No existing grants are widened.

Sample data adds refrigerator cleaning (due soon), laptop maintenance (overdue), a resolved
Samsung warranty claim and replacement headphone cushions. They are created in the same
transaction as the sample bills. Real service records are unaffected by removing samples.

## 4. Frontend

- Product page: service summary, timeline, Add service record, edit/delete, attachment links.
- Add/edit dialog: existing TextField/Alert/Button and copy patterns, inline field errors,
  Ctrl+S / Cmd+S, date or repeat interval, and claim fields only for warranty claims.
- `/services`: search, type/due filters and links to each permanent product page.
- Dashboard: Services due card only when needed. Bell: Service due/overdue entries.
- Claim kit: Previous services timeline. Mobile layouts wrap; status always has icon and words.
- `lib/service-records.ts` holds types and endpoint functions; api.ts is the only fetch wrapper.

## 5. Verification

ServiceRulesTests covers the 30-day boundary, today, overdue, unknown and end-of-month/leap-year
interval maths. ServiceRecordApiTests covers CRUD/search/filtering, invalid values and future dates,
SAVED-only targets, authentication and cross-user 404s, attachment ownership/removal, stable product
identity, cascade deletion, current schedule replacement, product/dashboard/claim-kit summaries,
sample add/remove, and AI view isolation/base-table denial. Existing feature tests also run.

Run in backend: `./mvnw test`. Run in frontend: `npm run typecheck`, `npm run build`.
