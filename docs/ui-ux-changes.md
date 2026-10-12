# UI/UX changes — what changed and how to check it

Everything done in the UI/UX pass, in order, each with **where to look** and a
**checklist**. Tick the boxes as you verify. The reasons behind each change are in
[`ui-ux-review.md`](ui-ux-review.md).

## Before you start

1. **Restart the backend.** Section 11 adds two endpoints (`PUT /api/users/me`,
   `PUT /api/users/me/password`). In IntelliJ: stop and run **Backend** again.
2. The frontend (`npm run dev`) reloads on its own. If something looks stale, refresh with **Ctrl F5**.
3. For **phone** checks: F12 → the phone/tablet icon (Toggle device toolbar, `Ctrl Shift M`)
   → pick e.g. *iPhone 12 Pro*. Refresh after switching.
4. Have at least: one saved bill, one bill not read yet (upload any PDF), and Gmail connected
   if you have it set up.

URLs below assume `http://localhost:5173`.

---

## 1. Sidebar is navigation only

**Files:** `components/AppLayout.tsx`

- [ ] The sidebar no longer has an *Upload bill* button, a name/email card or *Logout*.
- [ ] *Upload bill* is in the top bar (right); name, email and *Sign out* are in the avatar menu (top right).

## 2. Collapsible sidebar (like Chrome's vertical tabs)

**Where:** any signed-in page, desktop width (≥ 1024 px). **Files:** `components/AppLayout.tsx`

- [ ] By default the sidebar is a thin strip of icons; the Bill Locker logo shows in the top bar.
- [ ] Hover the strip: after a moment it slides open **over** the page (with a shadow); move away and it closes.
- [ ] Click the panel icon at its top left: it stays open and the page moves right. Click again: back to the strip.
- [ ] Reload the page: your choice (open / strip) is remembered.
- [ ] Keyboard: Tab into the sidebar → it opens.

## 3. Money and the stat cards (task 1)

**Where:** `/home`, `/documents`. **Files:** `lib/documents.ts`, `pages/DashboardPage.tsx`, `components/dashboard/StatCard.tsx`

- [ ] Amounts show **₹**: list rows, spending chart, top shops (`₹1,499.00`).
- [ ] *Total spent* card shows whole rupees (`₹50,893`).
- [ ] Zeros are words: "Nothing new this month", "No spending this month", "No warranty dates yet".
- [ ] Fourth card is titled **Needs attention** on one line; the four big numbers line up.
- [ ] Hovering a cut-off grey line under a number shows its full text.

## 4. Account menu, signed out, session expired (task 2)

**Where:** avatar (top right). **Files:** `components/UserMenu.tsx`, `lib/api.ts`, `components/AuthProvider.tsx`, `components/RouteGuards.tsx`

- [ ] Menu: avatar + name + email, **Settings**, red **Sign out**. No "Gmail import".
- [ ] Keyboard: focus the avatar, Enter → ↑ ↓ move between items, Esc closes and returns focus.
- [ ] *Sign out* → sign-in page with green **"You've been signed out."**
- [ ] Session expired: open `/documents?status=SAVED`, F12 → Application → Local storage (or Session storage)
      → set `billLocker.token` to `abc` → click *Warranties*. You land on sign-in with blue
      **"Your session expired. Please sign in again."**; after signing in you're on `/warranties`.
- [ ] Wrong password at sign-in still just says "Incorrect email or password."

## 5. Buttons, dialogs and toasts (task 3)

**Files:** `components/Button.tsx`, `components/FeedbackProvider.tsx`, `lib/feedback-context.ts` (+ used across pages)

- [ ] All buttons share one look: same height (40 px), same corners; one filled indigo button per screen.
- [ ] No browser "localhost says…" pop-ups anywhere. Instead a styled dialog:
  - [ ] Document page → ⋯ → *Delete…*: red ⚠ dialog, **Cancel is focused**, Esc / clicking outside cancels.
  - [ ] Delete for real (test bill): back on the list, toast **"Document deleted."**
  - [ ] Edit a field → *Discard* → "Discard your changes?"
  - [ ] Edit a field → *← All documents* → "Leave without saving?"
  - [ ] Saved bill → *Read again* → "Read the bill again?" (indigo button: nothing is lost for good)
  - [ ] Remove a filled product (🗑 in a product card) → "Remove product 1?"
  - [ ] Gmail → *Disconnect* → red dialog (Cancel unless you mean it).
- [ ] Toasts (bottom right, dark, vanish after ~4 s): Gmail *Ignore* → "1 file ignored.", *Import* → "Importing 1 file…",
      *Restore* → "1 file moved back to To review."
- [ ] Busy buttons show a spinner and a verb: "Signing in…", "Creating account…", "Saving…", "Reading…", "Uploading…".

## 6. Upload in one click, drop anywhere (task 4)

**Files:** `components/UploadButton.tsx`, `components/DropAnywhere.tsx`, `lib/useUploadBill.ts`

- [ ] Top bar **Upload bill** opens the file picker straight away (no detour to Documents).
      After choosing, the bill opens and is read with AI.
- [ ] Drag a PDF from your desktop onto **any** signed-in page (dashboard, warranties…): a full-screen
      "Drop to upload your bill" layer appears; drop → it uploads and opens.
- [ ] Drag it back out of the window without dropping: the layer goes away.
- [ ] A wrong file type (e.g. `.txt`) → red toast with the backend's message.
- [ ] Dashboard's first-visit screen (new account): *Upload a bill* also opens the picker.

## 7. Documents page

**Where:** `/documents`. **Files:** `pages/DocumentsPage.tsx`

- [ ] With bills: the big drop zone is now one slim line ("Drop a bill anywhere…" + *Upload bill*); the list sits higher.
- [ ] New account (no bills): the big "Add your first bill" area instead.
- [ ] Grey placeholder rows while the list loads (throttle the network in F12 to see it).
- [ ] Set any filter or search → a **Clear filters** button appears and resets everything.
- [ ] Amounts show on phones too.

## 8. Document page

**Where:** open any bill. **Files:** `pages/DocumentPage.tsx`, `components/documents/MoreMenu.tsx`

- [ ] A bill **not read yet**: *Read with AI* is the filled indigo button.
- [ ] *Download* and *Delete* live in the **⋯** menu (Delete in red, with the dialog from 5).
- [ ] *Save details* is white when there's nothing to save, indigo after an edit.
- [ ] Browser tab title is the shop or file name, e.g. "Croma · Bill Locker".

## 9. Dashboard

**Where:** `/home`. **Files:** `pages/DashboardPage.tsx`, `components/dashboard/WarrantyHealth.tsx`

- [ ] Under the greeting: a real summary, e.g. "2 warranties active · nothing ends this month".
- [ ] When something waits: an amber banner on top, e.g. "**1 bill needs your review** · 1 to review · 1 in Gmail — Review now →". It opens the right list.
- [ ] *Total spent* card is a link now (opens saved bills), like the other three.
- [ ] Warranty health: "3 products have no warranty date. **Add dates** so we can remind you." → opens Warranties › No info.

## 10. Top bar: search and notifications

**Files:** `components/AppLayout.tsx` (`GlobalSearch`), `components/NotificationBell.tsx`, `lib/useAttention.ts`

- [ ] Search box in the top bar (tablet/desktop). Type a shop or product, Enter → `/documents?q=…`.
- [ ] `Ctrl K` from any page jumps into the search box.
- [ ] 🔔 shows a red count when something waits. Click: bills to review, bills that couldn't be read,
      files found in Gmail, warranties ending soon — each line opens the right place. Nothing waiting: "You're all caught up."
- [ ] The count updates after you act (e.g. save a bill, then change page).

## 11. Settings page (new) — frontend + backend

**Where:** avatar → *Settings*, or the ⚙ at the bottom of the sidebar → `/settings`.
**Files:** `pages/SettingsPage.tsx`, `lib/account.ts`; backend `user/UserController.java`, `UserService.java`,
`UpdateProfileRequest.java`, `ChangePasswordRequest.java`, test `user/UserApiTests.java`

- [ ] **Profile:** change your name (e.g. "tjain" → "Tanu Jain") → *Save name* → toast; the top bar and the
      dashboard greeting ("Good morning, Tanu") change straight away. *Save name* is greyed out until you change something.
- [ ] A 1-letter name → error under the field ("Name must be 2–80 characters").
- [ ] Email is shown read-only.
- [ ] **Password:** wrong current password → "Your current password is not correct." under the field — and you stay signed in.
- [ ] Weak new password → the checklist under it shows what's missing; the backend error appears under the field.
- [ ] Right current + good new → toast "Your password has been changed."; sign out and in with the new one.
- [ ] **Connected Gmail:** lists your addresses with "Scanned … ago"; button to the Gmail page.
- [ ] **Sign out** works from here too.

## 12. Sidebar badges and names

- [ ] "Gmail Import" is now just **Gmail**.
- [ ] Red count pills on *Documents* (to review + couldn't read), *Warranties* (ending soon), *Gmail* (files found).
      On the icon strip they're a small red dot.
- [ ] **Settings** is at the bottom of the sidebar.

## 13. Phones: bottom tab bar

**Where:** device toolbar, a phone size (< 640 px).

- [ ] A tab bar at the bottom: Dashboard · Documents · **(round Upload button)** · Warranties · Gmail, with badges.
- [ ] The round button opens the file picker.
- [ ] Toasts and the sticky Save bar (document page) / selection bar (Gmail) sit **above** the tab bar.
- [ ] The top bar shows logo, 🔔 and avatar (Settings is in the avatar menu).

## 14. Warranties and Gmail tabs

**Where:** `/warranties`, `/gmail`. **Files:** `components/Tabs.tsx`

- [ ] Both pages use the same tabs: underlined when chosen, counts in a pill.
- [ ] Warranties empty tabs say something specific: "Nothing ends in the next 30 days.", "Every product has a warranty date.",
      or "No products match "…"." for a search.
- [ ] Gmail with no account: the empty card has its own **Connect Gmail** button.
      With accounts: the header button reads **Connect another**.
- [ ] If Gmail isn't set up on the server, users see "Gmail import isn't available right now." (no `.env` names).

## 15. Sign-in screens

**Where:** sign out, then `/login`, `/register`, *Forgot password?*. **Files:** `pages/LoginPage.tsx`,
`RegisterPage.tsx`, `ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx`, `components/FormParts.tsx` (`PasswordRules`)

- [ ] Sign in: one sign-up link in the footer ("New here? **Create an account**"); the big outlined button and the "or" line are gone.
- [ ] Email placeholder is `you@example.com`; no placeholder repeating "Enter your password".
- [ ] *Forgot password?* always opens, even with the email empty; the email can be typed or changed there.
- [ ] Sign up: field is "Your name" (e.g. Tanu Jain); under the password a live checklist ✓ 8+ characters ✓ A letter ✓ A number.
- [ ] Reset password page: the same checklist.
- [ ] With the backend stopped, errors read "Can't reach Bill Locker right now…" (the port hint is in the browser console only).

## 16. Small polish

- [ ] Browser tab titles everywhere: "Dashboard · Bill Locker", "Documents · …", "Sign in to your locker · …".
- [ ] Small grey helper text is a shade darker (easier to read).

## 17. Share a saved bill on WhatsApp

**Where:** open a **saved** bill → the bar above the preview. **Files:** `lib/share.ts`,
`components/documents/DocumentPreview.tsx`, `pages/DocumentPage.tsx`

- [ ] A bill that isn't saved yet: no WhatsApp button. After *Save details*: a green **WhatsApp** button appears above the preview.
- [ ] **Chrome/Edge on Windows, or a phone:** click it → the system share sheet opens → pick **WhatsApp** → choose a contact.
      The PDF/photo is attached, with a message like "Bill from Croma · 12 Mar 2026 · Total ₹54,990.00", the products
      with their warranty end dates, and "Shared from Bill Locker". (WhatsApp Desktop must be installed to appear in the Windows sheet.)
- [ ] Close the share sheet without choosing: nothing happens (no error).
- [ ] **Browsers without file sharing (e.g. Firefox):** WhatsApp opens in a new tab with the message filled in, the file
      downloads, and a toast says to attach it with 📎.

## 18. Warranties only for products; a Bills & receipts page

**Files:** backend `warranty/WarrantyRules.java` (`hasWarranty`), `WarrantyService.java`, Gemini prompt;
frontend `pages/BillsPage.tsx`, `components/documents/DocumentList.tsx`, `components/AppLayout.tsx`, `App.tsx`.

- [ ] `/warranties` (and the dashboard's Products / Warranty health numbers) only show products from an
      **Invoice** or **Warranty card**, or products with warranty details (months, dates, provider). A ride or
      food receipt without them is gone from there.
- [ ] Sidebar: **Bills & receipts** (receipt icon) under Documents → `/bills`. Phones: **Bills** in the bottom bar.
- [ ] `/bills` lists only documents of type **Bill / receipt** (the old "Receipt"), with Bills & receipts count,
      Spent this month, Total, and a search (kept in the URL).
- [ ] New bills: the AI files rides, food, fuel, utility and phone bills as **Bill / receipt**. An older bill:
      open it, set **Document type → Bill / receipt**, save → it appears on `/bills` and leaves `/warranties`.

## 19. Bills & receipts: read automatically, saved without review

**Files:** backend `BillCategory.java`, `DocumentService.statusAfterRead`, `Document.category`, Gemini prompt;
frontend `DetailsForm.tsx` (slim form), `DocumentPage.tsx` (message), `DocumentList.tsx`, `BillsPage.tsx` (chips).

- [ ] Upload a ride / food / phone bill → after the AI read it says "Read and saved under Bills & receipts.
      Nothing to do…" and the status is **Saved** (not "Needs review"). It doesn't appear in **Needs attention** or 🔔.
- [ ] Its form shows only: Document type, **Category**, Shop or company, Date, Amount, Bill or receipt number.
- [ ] Change the type to **Invoice** → the full form (seller, buyer, products, warranty) comes back with its values.
- [ ] An invoice still waits for review ("Details read by AI. Please check them…").
- [ ] `/bills`: rows say the category (Travel, Food…), chips **All · Travel · Food …** with counts filter the list
      and the three totals; the chip is kept in the URL (`?category=`).

---

## Not done (and why)

| Idea from the review | Why not now |
|---|---|
| Delete account, export data, "sign out everywhere" | Need backend design (the local DB still has the old build's tables referencing `users`) |
| Preferences (currency, "expiring soon" days, reminder emails) | Reminders don't exist yet; ₹ is fixed for now |
| Change email | Needs re-verification of the new address |
| Terms & Privacy links | There are no such pages to link to yet |
| Sort the document list, bill thumbnails | Need backend support (sorting / page previews) |
| Phone: collapsible preview on the document page, dark mode, onboarding checklist, `?` shortcut sheet | Nice to have; next round |
