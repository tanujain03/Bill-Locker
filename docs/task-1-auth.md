# Task 1 — Sign-in first page, sign up, forgot password

This is the first task of the restart. You get a sign-in first page (split screen:
form on the left, what Bill Locker does on the right), accounts (sign up, sign in,
"remember me", sign out) and "forgot password" with a one-time reset link. The guide
says **what happens where**, so you can follow a request from the click to the
database and back.

## 1. The big picture

```
Browser (React, :5173)                    Spring Boot (:8080)                    PostgreSQL (BillLocker)
──────────────────────                    ───────────────────                    ───────────────────────
LoginPage ─ fetch POST /api/auth/login ─▶ SecurityConfig (open endpoint)
   (Vite proxy forwards /api → :8080)     AuthController.login
                                          AuthService.login ── findByEmail ────▶ users
                                          BCrypt: password matches hash?
                                          TokenService.issueFor → JWT
◀──────────────── { token, user } ───────
tokenStore.set(token, remember) (localStorage if "Remember me", else sessionStorage)
every later call: Authorization: Bearer <token> ─▶ SecurityConfig checks the JWT signature + expiry
```

## 2. Files: frontend (`frontend/src/`)

| File | Job |
|---|---|
| `main.tsx` | Starts React: `BrowserRouter` (URLs) → `AuthProvider` (who is signed in) → `App` |
| `App.tsx` | Which page belongs to which URL, and which URLs need sign-in |
| `lib/api.ts` | **The only place that calls the backend.** `tokenStore` keeps the token in localStorage ("Remember me": stays after the browser closes) or sessionStorage (gone when it closes). Adds the token and turns error JSON into an `ApiError` (`code`, `message`, `fieldErrors`) |
| `lib/auth-context.ts` | Types + `useAuth()`: any component can ask who is signed in |
| `components/AuthProvider.tsx` | Holds the user. On start-up it asks `GET /api/auth/me` whose saved token it is; `login`/`register` save the token; `logout` forgets it |
| `components/RouteGuards.tsx` | `RequireAuth` sends signed-out users to `/login`. `GuestOnly` sends signed-in users on to `/home`, which is also how you leave the login page after signing in |
| `components/AuthLayout.tsx`, `PromoPanel.tsx` | The split screen every sign-in page uses, 40 : 60 (`lg:w-2/5` + `w-3/5`): the form card on the left (grey), the Bill Locker headline + picture on the right (hidden below 1024 px width). Always exactly one screen high, so the page never scrolls; the picture is cut off at the bottom |
| `lib/useScreenScale.ts` | Measures the window (on load + every resize) and sets the root font size, so the whole sign-in screen scales to fit the real screen (section 2a) |
| `components/FormParts.tsx`, `Logo.tsx` | Shared UI: `TextField` (input + error text), `PasswordField` (adds the eye button: it switches the input between `type="password"` (dots) and `type="text"`), the button, message boxes, the logo |
| `pages/LoginPage.tsx` | **The first page** (`/` and `/login`): email, password, "Remember me", forgot-password link, "Create a new account" |
| `pages/RegisterPage.tsx` | The sign-up form. They don't check the rules themselves: the backend checks them and returns `fieldErrors`, which appear under each input |
| `pages/ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx` | The two halves of forgot password (section 4) |
| `pages/HomePage.tsx` | Placeholder signed-in page with **Sign out** |

### 2a. Fitting any screen size

The sign-in screens were designed at **1440 × 900**. `useScreenScale()` (called in
`AuthLayout`) reads `window.innerWidth/innerHeight` and works out a scale:

- width ≥ 1024 (split screen): `scale = min(width / 1440, height / 900)`, so both fit;
- narrower (card only): `scale = min(1, height / 780)`, so it only shrinks on short phones;
- always kept between 0.7 and 1.5.

It sets `<html style="font-size: 16 × scale px">`. Every Tailwind size (text, padding,
widths, icons) is in `rem` = "times the root font size", so everything scales
together: 1366×768 → 13.7 px, 1440×900 → 16 px, 1920×1080 → 19.2 px, 2560×1440 → 24 px.
A `resize` listener recalculates when the window changes, and leaving the page
puts the normal size back. (Breakpoints like `lg:` don't change: in media
queries `rem` always means 16 px.)

`vite.config.ts` forwards `/api/...` from :5173 to :8080. For the browser it's
one website, so the backend needs no CORS settings.

## 3. Files: backend (`backend/src/main/java/project/bill_locker/`)

| Package | Files | Job |
|---|---|---|
| `security` | `SecurityConfig` | The gate every request passes. The 4 auth endpoints are open; everything else needs a valid JWT. Also BCrypt and the JWT signing key (`JWT_SECRET`) |
| | `TokenService`, `JwtProperties`, `CurrentUser`, `JsonAuthenticationEntryPoint` | Make tokens; read `app.jwt.*`; get the user id from a token; answer 401 as JSON |
| `user` | `User`, `UserRepository`, `UserResponse` | The `users` table, its queries, and what the API shows (never the hash) |
| `auth` | `AuthController` | HTTP ↔ Java for `/api/auth/*`, and nothing more |
| | `AuthService` | Register (hash + save), login (compare hash), me |
| | `PasswordResetService`, `PasswordResetToken`(+`Repository`) | Forgot password (section 4) |
| | `ResetLinkSender`, `EmailResetLinkSender`, `LogResetLinkSender`, `ResetLinkSenderConfig` | "Send the link": by email over SMTP when `MAIL_USERNAME` is set, otherwise written to the log (section 4a) |
| | `*Request`, `AuthResponse`, `MessageResponse`, `ValidPassword` | Request/response shapes and validation rules |
| `common` | `BaseEntity`, `ApiException`, `GlobalExceptionHandler`, `ApiErrorBody` | id/created/updated columns; errors → `{ success:false, code, message, fieldErrors }` |

The layers are always **controller → service → repository**. The controller
never touches the database, and entities never go to the browser.

## 4. Forgot password, step by step

1. On the sign-in page you type your email and click **Forgot password?**. The link
   carries the email to `/forgot-password` in React Router's `state` (not in the URL, so
   it never lands in browser history or server logs). There it's shown read-only and you
   only click **Send reset link** → `POST /api/auth/forgot-password`. With an empty email
   box the sign-in page asks for it first; opening `/forgot-password` directly sends you back.
2. `PasswordResetService.requestReset`:
   - unknown email → do nothing, but give **the same answer** (so nobody can test which emails have accounts);
   - known email → delete the user's older tokens (only the newest link works),
     make 32 random bytes (`SecureRandom`) → the token, save only its **SHA-256 hash**
     with `expires_at = now + 30 min` in `password_reset_tokens`, and call
     `ResetLinkSender.send(email, "http://localhost:5173/reset-password?token=…")`.
3. With email set up (section 4a) `EmailResetLinkSender` emails the link. Without it,
   `LogResetLinkSender` prints this line in IntelliJ's Run window:
   `Password reset link for you@example.com (works once): http://localhost:5173/reset-password?token=…`
   Copy the link into the browser.
4. **`/reset-password?token=…`**: the page reads `token` from the URL. You type
   the new password twice. The match check happens only in the browser; the
   backend never sees "confirm". Then → `POST /api/auth/reset-password { token, password }`.
5. `PasswordResetService.resetPassword` hashes the token again and looks the hash
   up. Not found or expired → `400 INVALID_RESET_TOKEN`. Found → BCrypt the new
   password into `users.password_hash` and delete the token (it works once).
6. The page sends you to `/login` with "Your password has been changed".

**Why store a hash of the token?** Anyone who could read the database could otherwise
use a fresh link to take over the account. It's the same idea as storing password hashes.
SHA-256 is enough here (BCrypt isn't needed) because the token is 32 random bytes, far too many to guess.

## 4a. Sending the email (SMTP)

SMTP is the protocol mail servers speak. The backend logs in to an SMTP server (Gmail:
`smtp.gmail.com`, port 587), switches the connection to TLS (STARTTLS) and hands over the
email; Gmail then delivers it.

- `pom.xml`: `spring-boot-starter-mail` gives Spring's `JavaMailSender`, configured from
  `spring.mail.*` in `application.properties`, which reads `MAIL_HOST`, `MAIL_PORT`,
  `MAIL_USERNAME`, `MAIL_PASSWORD` from `backend/.env`.
- `ResetLinkSenderConfig` decides once at start-up: `MAIL_USERNAME` set → `EmailResetLinkSender`,
  empty → `LogResetLinkSender` (with a warning in the log).
- `EmailResetLinkSender` builds the email (plain text + HTML with a button) and sends it.
  If sending fails it only logs why: the page must answer the same as for an unknown
  email, or it would reveal that the account exists. So **if no email arrives, look in
  the backend log** for "Could not send the reset email".

**Gmail setup** (Gmail won't accept your normal password from an app):
1. Google account → Security → turn on **2-Step Verification**.
2. https://myaccount.google.com/apppasswords → create one (name it "Bill Locker") → copy the 16 letters.
3. In `backend/.env`: `MAIL_USERNAME=you@gmail.com` and `MAIL_PASSWORD=<the 16 letters, no spaces>`.
4. Restart the backend. The log says "Password reset links are emailed from you@gmail.com via smtp.gmail.com".

**No email arrived? Check the backend log:**
- "no account has this email, so nothing was sent" → that address has no Bill Locker
  account. Reset emails only go to registered addresses. Sign up with it first.
- "MAIL_USERNAME is not set" (at start-up) → the `MAIL_*` lines are missing from `backend/.env`.
- "rejected MAIL_USERNAME/MAIL_PASSWORD" → use an App Password, written without spaces.
- "Password reset email sent to …" → Gmail accepted it; look in the inbox **and the Spam folder**.

Gmail sends at most ~500 emails a day from a normal account, which is plenty for development.

## 5. Database

Hibernate (`ddl-auto=update`) creates `password_reset_tokens` (id, user_id → users
with ON DELETE CASCADE, token_hash unique, expires_at, created_at, updated_at).
`users` already existed with the same columns, so existing accounts still sign in.

## 6. Try it

1. Backend: IntelliJ **Backend** run config (or `cd backend` + `.\mvnw.cmd spring-boot:run`).
2. Frontend: `cd frontend` → `npm install` (once) → `npm run dev` → http://localhost:5173
3. The first page is sign in. "Create a new account" → sign up → you land on `/home`. Sign out → sign in again
   (untick "Remember me" and the sign-in ends when you close the browser).
4. Sign out → "Forgot password?" → your email → copy the link from the backend log →
   set a new password → sign in with it. Open the same link again: it says it has expired.

## 7. Tests

`backend/src/test/java/project/bill_locker/auth/`. Run with `.\mvnw.cmd test`
(needs Docker; it uses a throwaway PostgreSQL, never your database).
- `AuthApiTests`: sign up (+ invalid fields, duplicate email), sign in (right/wrong password, unknown email), `/me` with and without a token.
- `PasswordResetApiTests`: full flow, unknown email sends nothing, a link works once,
  a new request cancels the old link, made-up token / weak password rejected.
  `RecordingResetLinkSender` replaces the real sender so the test can "open the email".
- `EmailResetLinkSenderTests`: sends the email over real SMTP to GreenMail (a mail server inside the test) and checks subject, sender, link and button; a broken server is logged, not thrown.

## 8. Known limits (on purpose, for later tasks)

- The email is sent while the request waits (a few seconds with Gmail). Unknown emails answer faster, which could hint that an account exists; sending in the background would fix that.
- No rate limiting on login / forgot password.
- Sign-in tokens issued before a password reset stay valid until they expire (24 h).
