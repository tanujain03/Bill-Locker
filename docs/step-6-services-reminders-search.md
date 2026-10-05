# Step 6 — Service records, reminders and search

After this step, Bill Locker **keeps an eye on things for you**:

- **Service records:** log repairs and maintenance for a product, with the date of
  the next service.
- **Reminders and notifications:** a job runs every morning. It adds a notification
  (the bell icon) when a warranty is about to end or has just ended, and when a
  service is due. You also get a notice when a bill you uploaded has been read.
- **Search:** ask in plain English, for example "warranties expiring within 90 days"
  or "what did I buy from Croma?", and see how the question was understood.

Still no AI: the search uses simple rules. Later an AI model can take over the part
that *understands* the question, and nothing else has to change.

---

## 1. The big picture

```
You log a service           The reminder job (08:00 daily + at start-up)     The bell
──────────────────          ───────────────────────────────────────────     ────────
POST /api/service-records   ReminderJob → for each user → ReminderService    GET /api/notifications
  nextServiceDate ──────▶     warranty ends within 30 days → WARRANTY_EXPIRING  (polled every 30 s:
                              warranty ended ≤ 60 days ago → WARRANTY_EXPIRED    …/unread-count)
                              next service within 7 days   → SERVICE_DUE
Background reader (step 4)    (skipped if that reminder exists already)
  document read ────────────────────────────────────────▶ DOCUMENT_PROCESSED

Search box  →  POST /api/ai/search  →  SearchInterpreter: question → filters (rules)
                                    →  SearchService: filters → your products
                                    ←  { filters, explanation, results }
```

---

## 2. Where everything is

**Backend:** `backend/src/main/java/project/bill_locker/`

| File | What it does |
|---|---|
| `service/ServiceRecord`, `ServiceType` | A repair or maintenance visit (date, type, centre, cost, next date, notes) |
| `service/ServiceRecordInput`, `ServiceRecordResponse` | Request (with validation) and response shapes |
| `service/ServiceRecordRepository` | Queries; `latestPerProduct` = each product's most recent record |
| `service/ServiceRecordService`, `ServiceRecordController` | `/api/service-records` (list, add, edit, delete) |
| `notification/Notification`, `NotificationType` | A message for the bell; `dedupeKey` stops duplicates |
| `notification/NotificationService`, `NotificationController` | List, unread count, mark read, mark all read; the "document ready" notice |
| `notification/ReminderService` | **The reminder rules** (warranties and services) |
| `notification/ReminderJob` | **When** they run: `@Scheduled(cron = "0 0 8 * * *")` and at start-up |
| `search/SearchInterpreter` | **The search rules**: question → `SearchFilters`, plus the explanation sentence |
| `search/SearchService`, `SearchController` | `POST /api/ai/search`: applies the filters to your products |
| `product/ProductService` | `nextServiceDate` of each product, from its latest service record |
| `dashboard/DashboardService` | "Upcoming services" is filled in now |
| `document/DocumentService` | Sends the "Document ready for review" notification after reading |

**Frontend:** the screens already existed. `src/lib/features.ts` switches on
`services`, `notifications` and `search`. The search buttons say **Ask** and
**Smart search** instead of "AI", because rules do the work for now.

---

## 3. Service records

- A record belongs to a **product**, and through it to a user. That's why the
  ownership check is `findByIdAndProductUserId`: the id of the record, and the user
  of its product. Someone else's record is a 404.
- **The next service of a product** is the `nextServiceDate` of its **most recent**
  record. An older record's "next service" was the one you then did.
  `ServiceRecordRepository.latestPerProduct` (a `default` method on the repository
  interface) keeps the first record per product from a newest-first list.
- **A rule about two fields:** "the next service must be after the service date".
  An annotation checks one field, so `ServiceRecordService` checks this one and
  answers 400 with `fieldErrors.nextServiceDate`, the same shape as annotation errors.
- Deleting a product deletes its service records (ON DELETE CASCADE).

---

## 4. Reminders and notifications

**The rules** (`ReminderService.createReminders(userId, today)`):

| Notification | When | Example |
|---|---|---|
| `WARRANTY_EXPIRING` | 0–30 days left | "Your Inspiron 15 Laptop warranty expires in 9 days (10 Oct 2026)." |
| `WARRANTY_EXPIRED` | ended 1–60 days ago | "The warranty for your Double Door Refrigerator ended on 10 Sep 2026." |
| `SERVICE_DUE` | next service in 7 days, or up to 30 days overdue | "Your Split AC service is due in 3 days (4 Oct 2026)." |
| `DOCUMENT_PROCESSED` | the reader found a bill's details | "We’ve finished reading invoice.pdf. Review the details…" |

**Running twice must not send twice.** Every reminder gets a `dedupeKey`, for example
`WARRANTY_EXPIRING:<warranty id>:2026-10-10`. Before saving, the service asks
`existsByUserIdAndDedupeKey`. The table also has a unique constraint on
`(user_id, dedupe_key)` as a safety net. If you change the purchase date, the expiry
date changes, so the key is new and you get a fresh reminder. That's correct.

**When it runs** (`ReminderJob`):

- `@Scheduled(cron = "0 0 8 * * *")` means every day at 08:00. The cron fields
  are: second, minute, hour, day, month, weekday.
- `@EventListener(ApplicationReadyEvent.class)` also runs it once at start-up, so
  a day isn't missed if the app was off at 08:00. Thanks to the dedupe key, this is safe.

**Why two classes?** `@Transactional` works through a Spring *proxy*: only calls
that come **from another bean** get a transaction. If `ReminderJob` called its own
`@Transactional` method, it would run without one. So the job (when) and the rules
(what) are separate beans. `DocumentProcessor` and `DocumentService` in step 4 work
the same way.

**Mark all read** is one `UPDATE … SET is_read = true` statement (`@Modifying @Query`),
instead of loading every notification and saving it again.

---

## 5. Search (`SearchInterpreter` + `SearchService`)

The contract splits search into two parts:

1. **Understand the question** and turn it into `SearchFilters`. The contract plans
   an AI model for this; for now rules do it. Each rule is a regular expression:

   | You type | Becomes |
   |---|---|
   | "within 90 days", "in 2 months", "this year" + expir/warrant/end | `daysUntilExpiry` (soonest first) |
   | "expired", "under warranty", "expiring soon", "no warranty" | `warrantyStatus` |
   | "from Croma" (only if one of your products is from there) | `seller` |
   | one of your brands, e.g. "LG" | `brand` |
   | "appliances", "kitchen", "electronics" … | `categorySlug` |
   | "laptop", "fridge", "headphones" … (with synonyms: fridge = refrigerator) | `text` |
   | "over 50k", "under ₹2,000", "below 1 lakh" | `minPrice` / `maxPrice` |
   | "in 2025", "last year" | `purchasedAfter` / `purchasedBefore` |
   | "most expensive", "cheapest", "latest", "oldest" | `sortBy` (+ `limit 1`) |
   | anything else | the meaningful words become `text` |

2. **Run the filters** (`SearchService.apply`) on your own products: plain Java
   comparisons. **Nothing from the question is ever put into SQL**, so a question
   can't break or attack the database. That is the reason for the split.

The answer also explains itself: `"Showing products bought from Croma."`, and the
app shows each filter as a chip. `@JsonInclude(NON_NULL)` on `SearchFilters` leaves
the unused filters out of the JSON.

---

## 6. The database

Hibernate created two tables on start-up. No reset was needed.

| Table | Holds |
|---|---|
| `service_records` | `product_id` (CASCADE), `service_date`, `service_type`, `service_center`, `cost`, `next_service_date`, `notes` |
| `notifications` | `user_id`, `product_id` / `document_id` (CASCADE), `type`, `title`, `message`, `scheduled_at`, `is_read`, `dedupe_key` (unique per user) |

`NotificationType` already lists `GMAIL_BILLS_FOUND`, which Gmail import uses later.
Hibernate writes the allowed enum values into a CHECK constraint **once**, and
`ddl-auto=update` never changes it. So adding a value later would need a database
reset.

---

## 7. A development lesson: a half-compiled restart

While checking this step in the browser, the app suddenly signed out. The cause:

- IntelliJ runs the backend with **devtools**, which restarts the app when compiled
  classes change.
- `mvnw compile` (Maven's "incremental" build) first **deletes** all compiled
  classes, then compiles for a few seconds.
- Devtools noticed the deletions and restarted in the gap, without `SecurityConfig`.
  For a few seconds Spring Boot's *default* security answered every request with a
  401 (`WWW-Authenticate: Basic`), and the frontend treats any 401 as "signed out".

The fix: while the app runs in IntelliJ, build with
`mvnw compile -Dmaven.compiler.useIncrementalCompilation=false`. Despite its name,
that recompiles **only the changed files** and deletes nothing. Building inside
IntelliJ (Ctrl+F9) also only rewrites changed classes.

---

## 8. Run it

1. IntelliJ restarts the backend by itself (devtools). If it doesn't, re-run
   **Backend**. No new libraries this time.
2. At start-up the log shows `Reminders for 2026-…: N new`.
3. In the app:
   - **Services**: add a visit with a next service date within 7 days.
   - Restart the backend, or wait until 08:00, to get the reminder.
   - The **bell** shows it; clicking it opens the product and marks it read.
   - Try the **search box**: "warranties expiring within 90 days", "most expensive
     purchase", "what did I buy from …?".

## 9. Tests

There are 62 backend tests (`./mvnw test`). The new ones:

- **`ServiceRecordApiTests`:** add, edit, delete; the product's next service comes
  from its latest record; date rules; someone else's records and products are 404;
  deleting a product deletes its history.
- **`NotificationApiTests`:**
  - the job makes exactly the right reminders, and **running it twice adds nothing**
  - mark read, mark all read, someone else's notification is 404
  - a read bill sends "Document ready for review"
- **`SearchInterpreterTests`:** the rules, without a database: expiry windows, status,
  seller, brand, kinds, prices, years, order, the fallback, and the explanation.
- **`SearchApiTests`:** real questions against real products, and an empty question
  is refused.

The reminder job is off in tests (`app.reminders.enabled=false`). Tests call
`ReminderService.createReminders(userId, today)` themselves.

## 10. Next

- **Gmail import:** find bills in your inbox (OAuth2).
- **AI:** better reading of messy bills, and an AI model for the "understand the
  question" part of search. The filters and the safe query stay as they are.
- **Email reminders** as well as the bell, if wanted.
