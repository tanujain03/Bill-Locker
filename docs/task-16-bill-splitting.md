# Task 16 — Split bills with friends

Create a group for a trip, meal or shared purchase. Add named guests, split expenses equally or
with custom amounts, and record payments. Friends connect through personal invitation links;
there is no public user directory or contact upload. No new dependencies or payment integration.

## 1. What happens where

`billsplit`: Controller → `BillSplitService` → repositories. Requests and views are records.
Hibernate ddl-auto creates uniquely named `bill_split_groups`, `bill_split_members`,
`bill_split_invitations`, `bill_split_expenses`, `bill_split_shares`, `bill_split_settlements`,
and `bill_split_activity`. These do not reuse legacy tables. Existing private documents, spending
reports, service records and AI permissions are unchanged.

`frontend/src/lib/bill-splits.ts` defines types and calls the existing `api.ts` wrapper.
`SplitsPage` lists/creates groups; `SplitGroupPage` shows people, balances, expenses, payments and
activity. `components/splits/SplitForms.tsx` contains the expense, payment and invite dialogs.
`SplitInvitePage` handles `/split-invite#token=…`; login and sign-up retain the invitation return
path. Accepting always requires an explicit click after signing in.

## 2. How to try it

1. Open **Split bills** in the sidebar and create “Goa weekend”.
2. Add a friend's name as a guest. Guests can participate before creating an account.
3. Choose **Create invite link** beside that person. Copy it and send it yourself. The link expires
   after seven days. A new link invalidates the previous one; **Revoke link** disables it immediately.
4. Your friend signs in or signs up, checks the group and guest name, then accepts. They see only
   this group's ledger, not anyone's private bills. The invitation cannot be accepted twice.
5. Add an expense, its payer and participants. Choose equal shares or custom rupee amounts.
   Alternatively open a saved bill and select **Split bill**, then choose a group you own.
6. After paying outside the app, the debtor or group owner chooses **Record payment**. The
   recipient chooses **Confirm received**. A pending report does not change balances.

The first version uses INR throughout. One person paid each expense; the group owner maintains
expenses, guests and invitations. Joined members can view the group and report their own outgoing
payments. The owner can report guest payments and confirm receipt on behalf of an unclaimed guest,
but cannot confirm on behalf of another joined account. Unclaimed guest confirmations therefore
represent the organizer's record, not independent confirmation by that guest.

## 3. API

All endpoints below require a valid JWT. The caller ID comes only from that token.

| Endpoint under `/api/splits` | Job |
|---|---|
| `GET /groups` | List the caller's joined groups, including those they own. |
| `POST /groups` | `{name}` creates a group and its owner membership (201). |
| `DELETE /groups/{id}` | Owner permanently deletes the group and all shared rows for everyone (204). Original bills remain. |
| `GET /groups/{id}` | Members, balances, expense/share history, payments and latest 100 activity entries. |
| `POST /groups/{id}/members` | Owner adds `{name}` as a guest (201). Names are unique ignoring case; max 50 people. |
| `POST /groups/{id}/members/{memberId}/invitation` | Owner issues/reissues a guest's invitation; returns token, expiry and guest name. |
| `DELETE /groups/{id}/members/{memberId}/invitation` | Owner revokes the current link (204). |
| `POST /invitations/preview` | `{token}` returns only group name, guest name and expiry. |
| `POST /invitations/accept` | `{token}` claims that guest's membership for the caller; returns the group. |
| `POST /groups/{id}/expenses` | Owner adds an immutable expense snapshot (201). |
| `POST /groups/{id}/expenses/{expenseId}/void` | Owner voids an expense, retaining history. |
| `POST /groups/{id}/settlements` | Report a manual payment (201), initially PENDING. |
| `POST /groups/{id}/settlements/{settlementId}/confirm` | Recipient confirms the payment. |
| `POST /groups/{id}/settlements/{settlementId}/reject` | Reporter, recipient or owner rejects/cancels a pending report. |

Expense body:

```json
{
  "requestId": "client-generated-uuid",
  "title": "Dinner",
  "amount": 1200,
  "expenseDate": "2026-10-07",
  "payerId": "group-member-uuid",
  "mode": "EQUAL",
  "shares": [{"memberId": "member-1-uuid"}, {"memberId": "member-2-uuid"}],
  "sourceDocumentId": "optional-owned-saved-document-uuid"
}
```

CUSTOM uses the same shares array with an `amount` for each selected person. Amounts are
nonnegative, at most two decimal places and sum exactly to the positive expense total. Selected
members and payer must belong to this group. Date cannot be after `LocalDate.now(clock)`.

Settlement body: `{requestId, fromMemberId, toMemberId, amount}`. Amount must be positive, at
most two decimal places, no greater than the sender's debt or recipient's credit, and participants
must differ. Dates/expiry use the existing Clock bean. All errors use the existing ApiError shape.
Nonmembers and foreign-group records return 404; members without the required role get 403.
Invalid inputs have `fieldErrors`; changed duplicate requests and locked expenses return 409.
Expired/revoked/used invitations return 410; invalid/replaced tokens return 404.

## 4. Money, retries and concurrent changes

Equal splits operate on integer paise. Remaining paise go to participants in Java UUID sort order;
the browser uses the same signed ordering for its preview. No floating-point money arithmetic on
the backend. Custom shares must sum exactly; the owner can include themselves or leave themselves
out of the participants. Zero shares are allowed, but the total must be positive.

For each expense: payer balance increases by the total; each participant's balance decreases by
their share. Positive = is owed, negative = owes. Confirmed payments increase the debtor's balance
and decrease the recipient's balance. Balances sum to zero. Pending/rejected reports do not count.

The client retains a request UUID through submission retries. Expense and payment requests are
idempotent per group: matching retries return the existing result; different content with the same
request ID conflicts. Payment decisions are also idempotent. Group-row pessimistic locks serialize
all writes, including invitation acceptance. Confirmation rechecks current balances, so concurrent
or duplicate payment reports cannot settle the same debt twice. Group detail reads use a repeatable
snapshot. Refresh the group to fetch changes from other members; there is no live socket or email.

Expenses are immutable. The owner can void them only before the group has any pending or confirmed
payment. This conservative rule prevents changing already-settled debts. Voided expenses remain
visible and stop counting. Rejected payments do not prevent voiding. Adding new expenses remains
possible. The owner can permanently delete a group after the UI confirmation, including unpaid balances and
all shared history, members and invitations. Original bills are retained. There is no member removal,
currency conversion or item-by-item split.

## 5. Sharing and privacy

A source document is optional, must be SAVED and owned by the expense creator, and is checked on
first creation. Sharing snapshots only shop/bill number alongside the explicitly submitted title,
amount, date, payer and shares. The source UUID, original file, buyer contact details and other
locker data are never included in group responses. Editing/deleting the private document, or
removing sample data, cannot rewrite or remove the shared ledger snapshot.

Invitation tokens contain 32 cryptographically random bytes. Only SHA-256 hashes are stored.
Tokens appear in the link fragment, not server URLs, and are sent to authenticated POST endpoints
in request bodies. Whoever has a link can claim that named guest, so send it directly to the intended
person. Preview exposes no group expenses or membership list. An account can join a group only once.
No emails/messages are sent automatically. The AI role gets no SELECT on the new tables.

## 6. Verification

Backend MockMvc/Testcontainers tests cover group isolation, guest invites/reissue/revoke/expiry,
explicit acceptance and replay prevention, equal/custom arithmetic, validation, retry identifiers,
private receipt snapshots surviving document deletion, payment permissions, confirmation, void
locking, and concurrent invitation claims/payment confirmations. They also check that split tables
are inaccessible to the AI database role. Existing Gemini/Google fakes remain in use.

Frontend Node tests cover exact preview rounding (including signed UUID ordering), token handling,
authenticated API requests, stable retry identifiers, mutation methods and inline API errors.
Run from `frontend`: `node --test` with the files in `tests/`, `npm run typecheck`, `npm run build`.
Run from `backend`: `.\mvnw.cmd -Dmaven.compiler.useIncrementalCompilation=false test` (Docker required).
Forms use native modal focus handling, labels, status icons plus words, inline errors, copy buttons
and Ctrl/Cmd+S. Layouts stack on phones; the mobile navigation drawer includes Split bills.
