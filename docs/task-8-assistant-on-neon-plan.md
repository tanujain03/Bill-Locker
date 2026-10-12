# Task 8 (plan) — Chat assistant on Neon

Status: **built.** All steps below are done except step 6 (going live), which is yours.
Two details were added while building: questions run **one at a time**, and the id and
signature are sent as `?` parameters (see 2.4).

Live, the chat answers: *"The assistant is switched off: its database protection isn't in
place (see the backend log)."* On your PC it works. This document explains why, what the real
risk is, and how we fix it so the same code is safe on your PC **and** on Neon.

---

## 1. The problem

### 1.1 How the chat stays safe today (task 6)

Gemini writes the SQL, so we treat that SQL as **written by an attacker**: a cleverly worded
question (or a poisoned bill text) can make the AI write anything. Today one connection,
logged in as **the app's own database user**, does this for each question:

```
BEGIN READ ONLY
set_config('app.user_id', '<your id>')      ← who is asking
SET LOCAL ROLE bill_locker_ai               ← from here on: only the 6 ai_* views
<the AI's SELECT>
ROLLBACK
```

| Lock | Stops |
|---|---|
| read-only transaction | changing data |
| role `bill_locker_ai` | reading tables (password hashes, Gmail tokens…); only the views |
| views filter on `app.user_id` | seeing another user's rows |

### 1.2 The weak spot: `set_config` can be called inside a SELECT

1. `SELECT set_config('app.user_id', '<someone else>', true)` → the views now show **another
   user's bills**.
2. `SELECT set_config('role', 'neondb_owner', true)` → Postgres lets a session switch to any
   role the **logged-in user** belongs to. The logged-in user is the app's user, so the AI
   becomes the app user again and can read **every table**.

Our text check refuses SQL containing the word `set_config`, but it can be written without that
word (quoted/escaped names, or SQL text built inside another function). So the real protection
was in the database: `REVOKE EXECUTE ON FUNCTION set_config FROM PUBLIC` at startup.

### 1.3 Why that only works on your PC

Only the **owner** of `set_config` (Postgres's superuser) can take it away.

| | Logs in as | Superuser? | REVOKE | Chat |
|---|---|---|---|---|
| Your PC | `postgres` | yes | works | on |
| Neon | `neondb_owner` | no (Neon never gives superuser) | only a warning: *no privileges could be revoked* | **off** |

`ReadOnlyQueryRunner` checks `has_function_privilege('bill_locker_ai', 'set_config…')` before
every question, sees the hole, and refuses. **So the live site is not exposed; it is safely off.**

### 1.4 Proof (tried on a throw-away Postgres 17, not your database)

Logged in as a non-superuser `app` (like `neondb_owner`), with a table `secret_stuff`:

```
BEGIN READ ONLY; SET LOCAL ROLE ai;
SELECT current_user;                         → ai
SELECT set_config('role', 'app', true);      → app          (escaped!)
SELECT x FROM secret_stuff;                  → password hashes
REVOKE EXECUTE ON FUNCTION set_config … FROM PUBLIC;
                                             → WARNING: no privileges could be revoked
```

Logged in **directly** as a role `ai_login` that belongs to no other role:

```
SELECT set_config('role', 'app', true);      → ERROR: permission denied to set role "app"
SELECT x FROM secret_stuff;                  → ERROR: permission denied for table secret_stuff
```

### 1.5 Why "temporary views with the id written in" is not enough

It fixes weak spot 1 (no `app.user_id` to change) but not weak spot 2: the connection is still
logged in as the app user, so the AI can still switch back to it. The root cause is
**the AI's SQL runs on a connection logged in as a powerful user.**

---

## 2. The solution

Give the chat **its own database login with almost no rights**, and stop relying on anything
the AI could change.

```
Main app ──login: DATABASE_USERNAME (neondb_owner / postgres)──► everything, as now

Chat     ──login: bill_locker_ai (password AI_DATABASE_PASSWORD)──► SELECT on 6 ai_* views only
           a NEW connection per question, closed afterwards
```

| Attack the AI's SQL could try | Why it fails now |
|---|---|
| become the app user: `set_config('role', …)` | `bill_locker_ai` **belongs to no other role** → "permission denied to set role" |
| read tables directly | no rights on any table |
| pretend to be another user: `set_config('app.user_id', '<other id>')` | views also need `app.user_sig` = **signature** of the id, made with a secret the AI can't read (see 2.1) |
| switch off the 3 s limit: `set_config('statement_timeout', '0')` | the limit is enforced by **Java** (`Statement.setQueryTimeout(3)`): the backend cancels the query itself |
| keep session locks (`pg_advisory_lock`) after the question | the connection is **closed** after each question: Postgres drops all its locks and settings |
| change data | read-only transaction + no write rights (as before) |
| several statements / not a SELECT | text check (as before, only the first filter) |

### 2.1 The signature

- At startup the backend makes a table `ai_secret` with one long random value (only the main
  user can read it; `bill_locker_ai` gets no rights on it). Java reads it once and keeps it.
- For each question Java sets two values on the chat connection:
  `app.user_id = <id>` and `app.user_sig = sha256(secret + id)` (hex).
- Each view keeps only rows where the id matches **and** the signature is right:

  ```sql
  -- ME, used by every view: the user id, but only when its signature is correct
  (SELECT nullif(current_setting('app.user_id', true), '')::uuid
     FROM ai_secret s
    WHERE encode(sha256(convert_to(s.secret || current_setting('app.user_id', true), 'UTF8')), 'hex')
          = current_setting('app.user_sig', true))
  ```

  Views read `ai_secret` with **their owner's** rights (the main user), so this works although
  `bill_locker_ai` can't read the table. Changing `app.user_id` breaks the signature → no rows.
  `sha256()` is built into Postgres (no extension).

### 2.2 Why a new connection per question (no pool)

Chat questions are rare, so opening a connection (~0.1–0.3 s on Neon) is fine. In return,
**nothing** the AI's SQL did to its session (settings, locks) can outlive the question.

### 2.3 Still true after the change

- The same code runs on your PC and on Neon. No superuser needed anywhere.
- The `set_config` / `pg_settings` / advisory-lock REVOKEs are removed (no longer needed).
- Before each question the runner checks, on the chat connection itself, that it really is
  `bill_locker_ai`, not a superuser, and a member of no role; otherwise it refuses (as today).
- `bill_locker_ai` can still read Postgres's own catalog (table and role **names**), like any
  login. No user data is in there.
- Without `AI_DATABASE_PASSWORD` the chat says it is switched off; the rest of the app works.

### 2.4 Added while building: one question at a time

Postgres shows a login **its own other sessions** in `pg_stat_activity`, including their
current statement. All chat connections log in as `bill_locker_ai`, so the AI's SQL could look
at another user's chat session while it runs. So:
- the runner lets **one question run at a time** (others wait up to 10 s, then "busy, try
  again"); a closed connection is gone, so there is nothing to look at;
- the id and signature are sent as `?` parameters (`set_config('app.user_id', ?, true)`), so
  they never appear in any statement text.

The app runs as one Render instance, so a Java lock is enough. With several instances this
would need a database lock instead.

---

## 3. The plan (step by step)

### Step 1 — new setting
- `application.properties`: `app.assistant.db-password=${AI_DATABASE_PASSWORD:}`.
- `backend/.env.example`: add `AI_DATABASE_PASSWORD` (a long random value, e.g. `openssl rand -hex 32`;
  Neon refuses weak passwords).

### Step 2 — `AssistantDatabaseSetup` (startup, main connection)
1. Create or update the login: `CREATE ROLE bill_locker_ai LOGIN PASSWORD …` if missing, else
   `ALTER ROLE bill_locker_ai LOGIN PASSWORD …` (the old NOLOGIN role becomes a login).
   Skip all of this (with a warning) when the password is empty.
2. Remove `GRANT bill_locker_ai TO CURRENT_USER` and all `REVOKE … FROM PUBLIC` lines.
3. `CREATE TABLE IF NOT EXISTS ai_secret (secret text NOT NULL)`; insert a random value when
   empty (`gen_random_uuid()` twice); `REVOKE ALL ON ai_secret FROM PUBLIC`.
4. Views as today, but `ME` becomes the signed expression from 2.1.
5. `GRANT SELECT` on the 6 views to `bill_locker_ai` (as today).
6. Read the secret into memory for the runner.

### Step 3 — `ReadOnlyQueryRunner` (each question)
1. Text check (unchanged).
2. Open a connection: same `DATABASE_URL`, user `bill_locker_ai`, password from step 1
   (`DriverManager`, try-with-resources → always closed).
3. `autoCommit=false`, `SET TRANSACTION READ ONLY`.
4. Safety check on this connection: `current_user = 'bill_locker_ai'`, not superuser, member of
   no role. Else → "switched off" (as today).
5. `set_config('app.user_id', id, true)` and `set_config('app.user_sig', sig, true)` (parameters, not text).
6. Run the wrapped query (`cutValues`, 50 rows, 500 chars: unchanged) with `setQueryTimeout(3)`.
7. Rollback, close.

### Step 4 — tests (`ReadOnlyQueryRunnerTests`, Testcontainers)
Keep: user A never sees user B's rows; only one SELECT; values cut; 50 rows max.
Replace the `set_config` privilege tests with:
- forging `app.user_id` to user B (escaped spelling, not caught by the text check) → **no rows**;
- `set_config('role', <main user>, true)` → refused;
- reading `documents` / `users` / `ai_secret` directly → refused;
- `pg_sleep(10)` → stopped after ~3 s even after `set_config('statement_timeout', '0', true)`;
- `pg_advisory_lock(1)` → no advisory lock left afterwards;
- no `AI_DATABASE_PASSWORD` → "switched off".
Then the whole suite: `.\mvnw.cmd test`.

### Step 5 — docs
- `docs/task-6-assistant.md`: replace the "set_config is revoked" part with this design.
- `CLAUDE.md`: one line about the chat login and `AI_DATABASE_PASSWORD`.

### Step 6 — go live (you)
1. Add `AI_DATABASE_PASSWORD=<random>` to your `backend/.env`, restart IntelliJ's Backend, try the chat.
2. Push → add the **same kind** of value (may be a different one) as `AI_DATABASE_PASSWORD`
   on Render → Environment → Save (it redeploys).
3. Render log: `Assistant views and the bill_locker_ai login are ready`. Ask the chat a question.

---

## 4. Size and cost

- Changed: `AssistantDatabaseSetup`, `ReadOnlyQueryRunner`, their tests, `application.properties`,
  `.env.example`, two docs. No new library, no new table for app data (only `ai_secret`).
- One more secret to keep (`AI_DATABASE_PASSWORD`).
- Each chat question opens one extra short connection (~0.1–0.3 s).
