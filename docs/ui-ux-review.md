# UI/UX review — Bill Locker

> **Status:** all tasks in the table at the end are done. What changed and how to check
> each item: [`ui-ux-changes.md`](ui-ux-changes.md).

A designer's pass over the whole signed-in app and the sign-in screens, written for the
**end user**: someone who just bought a TV, has a pile of bills in Gmail, and wants to
know "is this still under warranty?" in under 10 seconds.

Each item says **what's wrong → why it matters → what to do**. Items are grouped and
then ordered at the end into small tasks you can build one at a time.

---

## 0. Already done (this change)

- **Sidebar is navigation only.** Removed the sidebar's *Upload bill* button, the
  name/email card and *Logout*. Each now appears exactly once:
  - *Upload bill* → top bar (right). On phones it shrinks to an icon, so it's always reachable.
  - Name, email and *Sign out* → the avatar dropdown at the top right.
- Why: the same control in two places makes people wonder whether they do different
  things, and the sidebar footer was a second "who am I" next to the top-right one.
- **Collapsible sidebar, like Chrome's vertical tabs** (desktop). By default it's a thin
  rail of icons; hovering (or tabbing into) it slides it open *over* the page; the
  button at its top left pins it open, and the page then moves over. The choice is
  remembered in this browser (`localStorage`, `billLocker.sidebarPinned`). While it's
  a rail, the logo shows in the top bar. Phones keep the ☰ drawer.

---

## 1. First impression: does it look good?

**Yes, the base is solid.** It's clean, has plenty of white space, uses one brand colour
(indigo), rounded cards and readable type. Status colours always come with an icon and a
label, and the empty and loading states are thought through. It already looks like a
real product.

What keeps it from looking **polished**:

| Problem | Where you see it |
|---|---|
| Money has no currency symbol | "Total spent **50,892.88**", "**0.00** this month" |
| Zeros shown as news | "**0** bought this month", "0.00 this month" — reads as bad news on every visit |
| Cards of different heights | "Needs your attention" wraps to two lines, so its number sits lower than the other three |
| The name looks like a username | "Good evening, **tjain**" / avatar "T" — the account name is a login handle, not a person's name |
| Empty top bar | On desktop the left 70% of the top bar is blank |
| Empty sidebar | Now that the footer is gone, ~60% of the sidebar is white space (fine, but see §3) |
| Big grey "nothing" box | *Expiring soon* fills half a row with a grey box saying nothing expires |

### Fixes
1. **Show the currency: `₹50,892.88`** (`formatAmount` → `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })`).
   Later this could come from a *Preferences → Currency* setting.
2. **Hide zero sub-lines or turn them into a nudge.** "0 bought this month" → hide it, or
   "Nothing new this month". "₹0.00 this month" → "No spending this month".
3. **Same height and alignment for all stat cards.** Shorter label ("Needs attention"),
   `items-stretch` on the grid, and the number always on the same baseline.
4. **Encourage a real display name.** Label sign-up's field "Your name" (e.g. *Tanu Jain*),
   and let users change it in Profile (§4). The greeting uses the first name.
5. **Make empty panels smaller and friendlier.** "Nothing expires in the next 30 days"
   with a ✓ icon and one line of height, not a large grey block. Put the free space to
   use, e.g. "Add warranty dates to 3 products →" (there are 3 with *No info*).

---

## 2. Header & account (top right)

The avatar dropdown is now the **one** place for the account. Make it complete:

```
┌──────────────────────────┐
│ Tanu Jain                │
│ tanuokruti@gmail.com     │
├──────────────────────────┤
│ 👤 Profile               │
│ ⚙  Settings              │
│ ✉  Connected Gmail       │
│ ?  Help & feedback       │
├──────────────────────────┤
│ ⎋  Sign out        (red) │
└──────────────────────────┘
```

- Remove *Gmail import* from this menu: it's already in the sidebar, and duplicates are
  exactly what we just removed. "Connected Gmail" (which manages accounts) is different and belongs here.
- Say **"Sign out"** everywhere (it pairs with "Sign in"; the old sidebar said "Logout").
- After signing out, show "You've been signed out" on the sign-in page.
- **Expired session:** today an expired token shows an error on every page. Instead,
  any 401 should sign the user out and send them to `/login` with "Your session
  expired — please sign in again", then return them to the page they were on.

### Use the empty top bar
Left to right: **page title / breadcrumb** · **global search** (bills, products, serial
numbers; `Ctrl K`) · 🔔 **notifications** (warranties expiring, bills to review, Gmail
files found) · **Upload bill** · **avatar**.
The bell gives the "Needs your attention" card a home that works on every page.

---

## 3. Sidebar & navigation

- It's navigation only now. Good. Add **Settings** at the bottom (a sidebar's
  bottom is where people look for it), separated by a line.
- Rename **"Gmail Import" → "Gmail"** (or "Inbox import"); short labels scan faster.
- When there's something to do, show **count badges** on links: *Documents* `2` (to
  review), *Gmail* `1` (found). Users then see work waiting without opening the dashboard.
- **Phones:** the drawer is fine, but a **bottom tab bar** (Home · Documents · ＋ · Warranties · Gmail)
  with a big centre **＋ Upload** is what people expect from a "snap a bill" app.
- Set the **browser tab title** per page ("Documents · Bill Locker",
  "Croma invoice · Bill Locker"). Right now every tab says "Bill Locker".

---

## 4. Profile & Settings page (new: `/settings`)

There is no way today to change your name or password while signed in, or to manage
your data. One page, sections on the left:

| Section | Contents |
|---|---|
| **Profile** | Name, email (read-only for now), avatar initials preview |
| **Security** | Change password (current + new, same rules as sign-up), sign out everywhere |
| **Connected accounts** | Gmail addresses, last scan, Disconnect (moved/linked from `/gmail`) |
| **Preferences** | Currency, date format, "expiring soon" = 30/60/90 days, reminder emails on/off |
| **Your data** | Export all bills (ZIP/CSV) |
| **Danger zone** | Delete account (type your email to confirm) |

This is frontend + backend work. It's a good next task.

---

## 5. Buttons & controls (consistency)

1. **One `Button` component** with variants `primary` (filled indigo), `secondary`
   (white + border), `ghost` (text only), `danger` (red) and sizes `sm`/`md`. Today
   button classes are copy-pasted in ~15 places with slightly different padding, weight
   and radius.
2. **One primary action per screen.** On the dashboard the filled button is *Upload
   bill* (top bar); *Import from Gmail* is secondary. Keep it that way. On a document
   that isn't read yet, **"Read with AI"** should be the filled one.
3. **Busy states with a spinner and a verb:** "Signing in…", "Uploading…", "Importing 3 files…"
   instead of "Please wait…". The button keeps its width.
4. **Upload in one click.** *Upload bill* goes to Documents, where you click *Choose
   file* again. Make it open the file picker directly (or an upload dialog over the
   current page), and let a file be dropped anywhere in the app.
5. **Styled confirm dialogs instead of `window.confirm`** ("localhost says…"). Delete,
   Discard, Disconnect, Read again: a modal with a red action button. For Delete,
   even better: delete straight away with an **Undo** toast.
6. **Toasts** for short feedback (Copied, Saved, Imported 3 files, Disconnected) instead
   of alerts that stay and push the page down.
7. **Destructive actions behind a "⋯" menu** on the document page (Download, Delete),
   so Delete isn't one slip away from Copy.
8. **One tab style.** Gmail uses underline tabs, Warranties uses filled pills. Pick one
   (underline tabs + a count pill) for both.

---

## 6. Dashboard

- **Order by urgency, not by type.** When "Needs attention" > 0, show a slim banner
  first: "**2 bills need your review** · 1 new bill found in Gmail  [Review now →]".
  Then the stats, then charts.
- **Every stat card should behave the same.** Three are links, *Total spent* scrolls the
  page. Link it to Documents (or to the spending chart's section with an anchor).
- **Warranty health:** the bar is mostly grey (3 of 5 have no info). Turn that into the
  call to action: "3 products have no warranty date — **add them**" (links to the list
  filtered to *No info*).
- **Greeting subtitle:** replace the generic line with a useful summary, e.g.
  "2 warranties active · nothing expiring this month".

---

## 7. Documents

- **Collapse the drop zone** to a slim bar once the user has bills; first-time users
  keep the big one. The list is what returning users came for.
- **Filters as chips** with a **Clear all**. Only "month" is a chip today.
- **Sort** (newest, oldest, amount) and a **skeleton** while the list loads (it's blank now).
- Show the **amount on phones** too; it's often the thing people look for.
- **Thumbnails** of the first page instead of a generic PDF/image icon make bills recognisable at a glance.

## 8. Document detail

- Toolbar: primary action by status (*Read with AI* / *Save*), secondary *Copy all*,
  the rest in "⋯".
- The sticky save bar and Ctrl S are great. Keep them.
- On phones, put the preview in a collapsible section above the form, or behind a
  "View bill" tab, so the form isn't pushed far down.

## 9. Warranties

- Empty states per tab: "No warranties expiring in the next 30 days ✓" instead of "Nothing here."
- Show **days left** prominently ("25 days left") and sort expiring-soon first by default.
- Later: "Add to calendar" / reminder per product.

## 10. Gmail

- Put a **Connect Gmail** button **inside** the empty state (now it's only at the top right).
- The "not set up" box shows `GOOGLE_CLIENT_ID` / `backend/.env`. That's for developers.
  For users: "Gmail import isn't available right now." Same for the "port 8080" network error.
- Counts as pills ("To review `3`"), matching Warranties.

## 11. Sign in / Sign up

- **One sign-up call to action.** There's a big "Create a new account" button *and*
  the promo's "Create your free locker". Turn the form's one into a footer link:
  "New here? **Create an account**" (same as Register's "Already have an account? Sign in").
- **Forgot password?** always opens the page (pre-filling the email if typed), instead of
  first demanding an email.
- Placeholders repeat the labels. Drop them or use `you@example.com`.
- Sign-up password: a **live checklist** (✓ 8+ characters ✓ a letter ✓ a number) instead of a hint line.
- **Terms & Privacy** links under *Create account* (we ask for Gmail access, so trust matters).
- `useScreenScale` shrinks the whole screen on small laptops. Keep a minimum font size so
  text never drops below ~14px.

## 12. Accessibility

- Avatar menu: `role="menu"` / `menuitem` and ↑ ↓ keys.
- Warranties "tabs" are buttons with `aria-pressed`; use real `tablist`/`tab` like Gmail.
- `text-slate-500` on `text-xs` is borderline for contrast; use `slate-600` for small grey text.
- Keep the focus ring (it's good). Check every icon-only button has an `aria-label`
  (the new phone Upload button does).

## 13. Nice to have (later)

- Dark mode (the colours are already in one place in `index.css`).
- Keyboard shortcuts sheet (`?`).
- A short **onboarding checklist** on the empty dashboard: ① upload a bill ② connect Gmail ③ add a warranty date.

---

## Suggested order (one task at a time)

| # | Task | Size | Frontend / backend |
|---|---|---|---|
| ✓ | Sidebar = navigation only; account + Upload in top bar | S | FE |
| ✓ 1 | Currency symbol, hide zero sub-lines, equal stat cards | S | FE |
| ✓ 2 | Avatar menu (no duplicate Gmail link, real menu keyboard) + expired-session redirect + "Signed out" notice. Profile/Settings items come with task 5 | S | FE |
| ✓ 3 | `Button` (+ `buttonClass` for links), confirm dialog + toasts (`useFeedback`), used everywhere | M | FE |
| ✓ 4 | Upload opens the file picker directly / drop anywhere | S | FE |
| ✓ 5 | Profile & Settings page (name, change password, preferences) | M | FE + BE |
| ✓ 6 | Dashboard attention banner + "add warranty dates" nudge | S | FE |
| ✓ 7 | Sign-in page clean-up (one sign-up CTA, forgot-password, checklist) | S | FE |
| ✓ 8 | Top bar: page title, global search, notification bell | L | FE + BE |
| ✓ 9 | Phone bottom tab bar | M | FE |
