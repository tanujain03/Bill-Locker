# Task 9 — Expense tracker

After this task:

- **An Expenses page** (`/expenses`, "Expenses" in the sidebar and on the phone tab bar)
  shows the spending of a **period**: **This month**, **Last month**, **Last 6 months**,
  **Last year** or a **Custom range** (two dates). For it: the total, the change against
  the period before, the daily average, where it ends up at this pace (while it runs), a
  **budget** meter, money **per category**, short **insights**, a **day-by-day calendar**
  (periods up to 62 days), a **month-by-month trend**, the **biggest purchases** (product
  lines) and the **top shops**. The period lives in the URL (`/expenses?period=6-months`,
  `/expenses?period=custom&from=2026-09-05&to=2026-09-25`; nothing = this month).
- **The chat assistant knows about it**: it can read the monthly budget and counts
  spending, categories and periods with the same rules as the page.
- **The dashboard's "Total spent" card** (and the Spending panel's "View all") opens it.
- **Every bill has a spending category now**, not only receipts: invoices too (a phone is
  Electronics, a fridge Appliances). Five categories were added: `ELECTRONICS`,
  `APPLIANCES`, `FASHION`, `HOME`, `ENTERTAINMENT`. Gemini picks one when it reads a bill;
  the review form shows a Category drop-down for every type.
- **The Documents page understands `?from=&to=`** (bought in that period) **and
  `?category=`** (a category name, or `NONE` for bills without one). Each category row on
  the Expenses page opens its bills of that period.

One new column (`users.monthly_budget`), no new tables, no new libraries: the charts are
plain HTML.

---

## 1. The big picture

```
Browser                                    Backend
ExpensesPage ── GET /api/expenses?from=&to= ─▶ ExpenseController → ExpenseService.report
                                              ├─ DocumentRepository.findSavedWithItems(user)  (saved bills + products, 1 query)
                                              ├─ bills in the period + bills in the period before
                                              └─ categories / products / shops / days / months
             ── PUT /api/expenses/budget ──▶ ExpenseService.setBudget → users.monthly_budget
```

**Why the backend adds it up:** like the dashboard, the rules ("only saved bills count",
"a bill without a total adds no money", "daily average of this month = ÷ days so far")
live in one Java class with tests. The browser only draws, plus the insight sentences,
which are just words around numbers the backend already sent.

**Which bills count:** saved bills with a purchase date in the period (both days
included). A bill without a total still counts as a bill of its period (it adds ₹0), so
"7 bills" matches the list the "See all 7 bills" link opens.

**The periods** (worked out in the browser, `presetRange` in `lib/expenses.ts`; the backend
only gets two dates):

| Button | From | To |
|---|---|---|
| This month | the 1st of this month | its last day (so "at this pace" works) |
| Last month | the 1st of last month | its last day |
| Last 6 months | the 1st of the month 5 months ago | today |
| Last year | the 1st of the month 11 months ago | today (the last 12 months, whole months) |
| Custom range | the date you pick | the date you pick (at most ~3 years) |

Clicking a month's bar shows that one month. A range that matches a button lights up that button.

## 2. The numbers (`expense/ExpenseService.java`)

| Number | Rule |
|---|---|
| Total | Sum of the period's saved bills' `totalAmount` |
| Period before | Whole months → the same number of whole months before (October → September). Anything else → the same number of days just before (5–25 Sep → 15 Aug–4 Sep) |
| Change | Against the period before; the page shows `%`, or rupees when it's ≥ 100% |
| Daily average | Total ÷ the period's days up to today (today included) |
| At this pace | Only while the period runs (started, ends after today): daily average × all its days |
| By category | Per `category`, biggest first; `null` = not sorted yet, shown as "Not sorted yet" with a link to sort them |
| Biggest purchases | The month's product lines with a name and a unit price, most expensive first (6). Bills have no quantity, so a line = its unit price |
| Top shops | Like the dashboard, but only the period ("Croma" and "croma " are one shop) |
| Days | Every day of the period (zeros too), for the calendar; empty above 62 days |
| Months | Whole months ending with the period's last month: the period's months, at least 12 |

Errors: a date that isn't `YYYY-MM-DD` → `400 INVALID_DATE`; from after to → `400
INVALID_RANGE`; longer than ~3 years (1100 days) → `400 RANGE_TOO_LONG`.

"Today" comes from the `Clock` bean, so tests pin it to 7 Oct 2026.

## 3. The budget

`PUT /api/expenses/budget {"amount": 25000}` stores it on the user (`users.monthly_budget`,
added by Hibernate's `ddl-auto=update`: a new nullable column, nothing to reset).
`{"amount": null}` removes it. Zero or negative → `400` with `fieldErrors.amount`.

The budget is **per month**. For a longer period the meter uses the budget × the months it
covers ("Budget ₹25,000 × 6 months"); a custom range that doesn't start on the 1st counts
days ÷ 30.44 as months. The meter's colour says the state, and an icon and words always
say it too: **fine** (under 80%), **close** (80% or more) or **over**. While the period
runs it also warns when the pace would take you over ("At this pace you'll go over by ₹…").

## 3b. The chat assistant

The assistant (task 6) writes its own SQL, so it needs to know the rules, not call this API:

- `ai_me` has a new column `monthly_budget` (`AssistantDatabaseSetup`; views are dropped
  and created again at start-up, so a new column needs nothing else).
- Its prompt (`GeminiAssistantModel.PLAN_PROMPT`) now says: spending = saved bills by
  purchase date (like the Expenses page), per category with null = "not sorted yet",
  product spending from `unit_price`, the same meaning of "this month", "last month",
  "last 6 months" and "last year" as the buttons, and how to answer budget questions.
  Asked to change the budget, it points to the Expenses page (it never changes data).

So "How much of my budget is left?" and "How much did I spend on food in the last 6
months?" give the same numbers as the page.

## 4. Categories for every bill

| Before | Now |
|---|---|
| `category` only kept for `RECEIPT` (parser and save both dropped it for other types) | Kept for every type: `GeminiAnswerParser`, `SaveDocumentRequest` and the form's `fromForm` |
| Prompt: "category: only for a RECEIPT" | Prompt: "what the money was spent on, for every document type … pick the category of the most expensive product" |

`category` is still plain text in the database (`BillCategory.parse` turns unknown text
into `OTHER`), so the new values need no database change. Bills saved before this task
have no category; the Expenses page lists them under "Not sorted yet". The chat
assistant's view description lists the new values too (`AssistantDatabaseSetup`).

## 5. Files

Backend (`backend/src/main/java/project/bill_locker/`):

| File | Job |
|---|---|
| `expense/ExpenseController.java` | `GET /api/expenses?month=`, `PUT /api/expenses/budget` |
| `expense/ExpenseService.java` | All the adding up for a period; the period before; the range checks |
| `expense/ExpenseReport.java` | The answer's shape (records) |
| `expense/BudgetRequest.java` | The budget, with its validation |
| `user/User.java` | `monthlyBudget` column |
| `document/BillCategory.java` | 5 new categories |
| `document/ai/GeminiDetailExtractor.java`, `GeminiAnswerParser.java`, `SaveDocumentRequest.java` | Category for every type |
| `assistant/AssistantDatabaseSetup.java`, `assistant/GeminiAssistantModel.java` | `ai_me.monthly_budget`; the spending rules in the prompt |

Frontend (`frontend/src/`):

| File | Job |
|---|---|
| `pages/ExpensesPage.tsx` | The page: period buttons + custom dates (URL), loading, empty period, the grid |
| `lib/expenses.ts` | Types, the two calls, the periods (`presetRange`, `rangeLabel`, `previousLabel`…), category icons, links |
| `components/expenses/SpendHero.tsx` | The big card: total, change, average, pace, budget meter + form |
| `components/expenses/CategoryBreakdown.tsx` | One bar per category: share %, change vs last month, link to its bills |
| `components/expenses/Insights.tsx` | Up to 4 sentences: budget pace, top category, biggest rise, busiest day, biggest bill |
| `components/expenses/DailyCalendar.tsx` | The month as a calendar, shaded by spending (one colour, 5 shades + a key) |
| `components/expenses/MonthTrend.tsx` | One bar per month (the period's months dark); clicking one shows that month |
| `components/expenses/TopProducts.tsx` | Biggest purchases, each opening its bill |
| `pages/DocumentsPage.tsx` | `?from=&to=` and `?category=` filters (+ chips to remove them) |
| `components/documents/DetailsForm.tsx` | Category drop-down for invoices too |
| `components/AppLayout.tsx` | "Expenses" in the sidebar and the phone tab bar (Gmail left the phone bar; the dashboard has "Import from Gmail") |
| `pages/DashboardPage.tsx`, `components/dashboard/SpendingChart.tsx` | "Total spent" and "View all" → `/expenses` |

Design choices: categories are told apart by **icon + label**, never by colour (14
categories would need 14 colours nobody can tell apart), so all bars share the brand
colour. The calendar uses one colour from light to dark, with a "Less … More" key.
Every bar and day shows its numbers on hover **and** keyboard focus.

## 6. Tests

`backend/src/test/java/project/bill_locker/expense/ExpenseApiTests.java`: empty account
(current month, 31 days, 12 months, previous = September), a past month (category order,
the month before, "not sorted yet", products, shops, days, months, biggest bill; unsaved
bills ignored), the pace for the current month, "last 6 months" (compared with the same
number of days before, no calendar), a custom range of 6 days, bad ranges, setting and
removing a budget, and sign-in required. `ReadOnlyQueryRunnerTests` checks the assistant
can read `monthly_budget`. `BillsAndReceiptsApiTests` and `GeminiAnswerParserTests` now check
that an invoice keeps its category.
