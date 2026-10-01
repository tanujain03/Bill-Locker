# Step 3 — A basic backend: accounts and document upload

This step gives Bill Locker a real backend for three things:

- **registering** an account
- **logging in**
- **uploading, viewing, downloading and deleting documents**

Files are stored inside your PostgreSQL database. Everything else (reading
documents with OCR, products, warranties, Gmail…) is added in later steps. The full
target design is kept on the git branch `step-2-database`.

This guide explains **what happens, and in which file**, for every request.

---

## 1. The big picture

```
Browser (React, http://localhost:5173)
   │  fetch /api/...            the frontend always calls /api/...
   ▼
Vite dev server ── proxy ──►  Spring Boot backend (http://localhost:8080)
                                 │
                                 ├─ 1. Security filter chain   (security/SecurityConfig)
                                 │      Is there a valid token? If not → 401, stop here.
                                 ├─ 2. Controller              (…/…Controller)
                                 │      HTTP → Java: reads the URL, JSON body, uploaded file
                                 ├─ 3. Service                 (…/…Service)
                                 │      The actual rules: check, decide, save
                                 ├─ 4. Repository              (…/…Repository)
                                 │      Java → SQL (Spring Data writes the queries)
                                 ▼
                             PostgreSQL "BillLocker"  (tables: users, documents, document_files)
```

If something goes wrong anywhere, **`common/GlobalExceptionHandler`** turns the
exception into the JSON error the frontend understands:

```json
{ "success": false, "code": "DOCUMENT_NOT_FOUND", "message": "The requested document was not found." }
```

---

## 2. Where everything is

All backend code is in `backend/src/main/java/project/bill_locker/`:

| Package / file | What it does |
|---|---|
| `BillLockerApplication` | `main()` starts Spring Boot, which finds every class below by its annotation |
| **security/** | |
| `SecurityConfig` | Which URLs are open (register, login) and which need a token (everything else); BCrypt; JWT signing key |
| `TokenService` | Creates the login token (JWT) after register/login |
| `JsonAuthenticationEntryPoint` | The 401 answer when a token is missing, expired or changed |
| `JwtProperties` | Reads `app.jwt.secret` / `app.jwt.expiration` from `application.properties` |
| `CurrentUser` | Gets the user id out of the verified token |
| **auth/** | |
| `AuthController` | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| `AuthService` | Creates accounts (hashes the password), checks passwords, returns a token |
| `RegisterRequest`, `LoginRequest`, `AuthResponse` | The JSON shapes going in and out; validation rules are annotations |
| **user/** | |
| `User` | Entity → table `users` |
| `UserRepository` | `findByEmail`, `existsByEmail`, … |
| `UserService`, `UserController`, `UpdateProfileRequest`, `UserResponse` | Profile: read it, change the name (`PUT /api/users/me`) |
| **document/** | |
| `Document` | Entity → table `documents` (name, type, size, owner, status) |
| `DocumentFile` | Entity → table `document_files` (the file bytes) |
| `DocumentController` | `POST /api/documents/upload`, `GET /api/documents`, `GET /api/documents/{id}`, `GET …/{id}/download`, `DELETE …/{id}` |
| `DocumentService` | Checks uploads, saves them, reads them back — always only the caller's own documents |
| `AllowedFileType` | Recognises PDF/JPEG/PNG/WEBP by their first bytes |
| `DocumentRepository`, `DocumentFileRepository` | Database access |
| `DocumentSummary`, `DocumentDetail`, `DownloadedFile` | The shapes returned to the frontend |
| `DocumentType`, `ProcessingStatus` | Enums (stored as text) |
| **common/** | |
| `BaseEntity`, `AuditableEntity` | `id` (UUID), `created_at`, `updated_at` for every table |
| `ApiException`, `ApiErrorBody`, `GlobalExceptionHandler` | One error format for the whole API |

Settings: `backend/src/main/resources/application.properties`, with your real values in
`backend/.env` (git-ignored). Tests are in `backend/src/test/java/project/bill_locker/`.

---

## 3. The database (3 tables)

Hibernate creates these from the entity classes when the backend starts
(`spring.jpa.hibernate.ddl-auto=update`).

**users**

| column | type | notes |
|---|---|---|
| id | uuid | primary key |
| name | varchar(80) | |
| email | varchar(254) | unique, always lower-case |
| password_hash | varchar(100) | BCrypt hash, e.g. `$2a$10$…` — never the password |
| created_at, updated_at | timestamptz | |

**documents** — one row per uploaded file (the "catalogue card")

| column | type | notes |
|---|---|---|
| id | uuid | primary key |
| user_id | uuid → users.id | owner; deleting the user deletes their documents |
| document_type | varchar(20) | INVOICE, WARRANTY_CARD, SERVICE_RECEIPT, REPAIR_RECEIPT, OTHER |
| file_name | varchar(255) | cleaned: never a path |
| mime_type | varchar(100) | detected from the content |
| file_size | bigint | bytes |
| processing_status | varchar(20) | always UPLOADED for now |
| created_at, updated_at | timestamptz | |

**document_files** — the file itself

| column | type | notes |
|---|---|---|
| document_id | uuid → documents.id | primary key *and* foreign key (same id as the document) |
| data | bytea | the bytes, up to 10 MB |

Why two tables? Listing your documents reads only `documents` (small rows). The
heavy `data` column is read only when you open or download one document.

Look at them yourself in pgAdmin: *BillLocker → Schemas → public → Tables →
users → View/Edit Data*. You will see the BCrypt hash, not the password.

---

## 4. What happens when…

### …you register (`POST /api/auth/register`)

1. **Frontend** `RegisterPage` → `authService.register()` sends
   `{ "name", "email", "password" }`.
2. **SecurityConfig**: this URL is public, so no token is needed.
3. **AuthController.register** — `@Valid` checks the rules written on
   `RegisterRequest` (name 2–80 characters, a valid email, a password of 8+ characters
   with a letter and a number). If any rule fails, the response is `400 VALIDATION_ERROR`
   with a message for each field, shown under the form inputs.
4. **AuthService.register**
   - lower-cases the email and checks it isn't taken
     (`409 EMAIL_ALREADY_REGISTERED` if it is)
   - hashes the password with **BCrypt**: `passwordEncoder.encode(...)`
   - `users.saveAndFlush(user)` → `INSERT INTO users …`
5. **TokenService.issueFor** creates a signed JWT that says "user {id}, valid
   for 24 h".
6. The response is `201` with `{ token, tokenType: "Bearer", expiresAt, user }`. The frontend
   stores the token (`lib/token-storage.ts`) and opens the Documents page.

### …you log in (`POST /api/auth/login`)

1. **AuthService.login**:
   - finds the user by email
   - `passwordEncoder.matches(typed, storedHash)` hashes what you typed the same way
     and compares the result
2. A wrong password **and** an unknown email get the same answer:
   `401 INVALID_CREDENTIALS` "Incorrect email or password." Nobody can use the
   login form to find out which emails have accounts. (An unknown email is even
   checked against a dummy hash, so both cases take the same time.)
3. On success you get a new token, exactly as when you register.

### …any other request (the token check)

The frontend adds `Authorization: Bearer <token>` to every call
(`lib/api-client.ts`). Before any controller runs, Spring Security:

1. reads the header (`SecurityConfig.bearerTokenResolver`)
2. verifies it with `jwtDecoder()`:
   - the signature matches our secret (nobody changed it)
   - it hasn't expired
   - we issued it
3. if the token is missing or wrong, returns `401`
   (`JsonAuthenticationEntryPoint`). The frontend then clears the token and shows
   the login page.
4. if it's fine, puts the token in the request. Controllers receive it as
   `@AuthenticationPrincipal Jwt jwt`, and `CurrentUser.id(jwt)` gives the user id.

The user id **always** comes from the token, never from the request body. So
nobody can say "I am user 123".

`GET /api/auth/me` returns the user behind the token. The frontend calls it when
it starts, to know whether you are still logged in.

### …you upload a file (`POST /api/documents/upload`)

1. **Frontend**: `UploadDialog` → `documentService.upload()` sends
   `multipart/form-data` with a `file` part and an optional `documentType`.
2. **Spring** rejects files over 10 MB before your code runs
   (`spring.servlet.multipart.max-file-size`) → `413 FILE_TOO_LARGE`.
3. **DocumentController.upload** → **DocumentService.upload**:
   - rejects an empty file with `400 FILE_REQUIRED`
   - `cleanFileName`: `C:\fakepath\bill.pdf` becomes `bill.pdf` (never a path)
   - `AllowedFileType.detect` looks at the **first bytes**: `%PDF-` is a PDF,
     `FF D8 FF` is a JPEG, and so on. The extension must match what the bytes say.
     Otherwise the response is `415 UNSUPPORTED_FILE_TYPE`, so a renamed `.exe`
     is not accepted as a PDF.
   - saves **two rows in one transaction** (`@Transactional`): `documents`
     (details) and `document_files` (bytes). Either both are saved or neither.
4. The response is `201` with the `DocumentSummary` (`processingStatus: "UPLOADED"`).
   The dialog shows "Uploaded — saved in your locker".

### …you open or download a document

- `GET /api/documents` lists your documents, newest first (`DocumentRepository.findForUser`).
- `GET /api/documents/{id}` returns one document's details.
- `GET /api/documents/{id}/download` returns the bytes with the right `Content-Type`.
  The frontend shows them as the preview.
- `DELETE /api/documents/{id}` deletes the row. PostgreSQL deletes the file row
  too (`ON DELETE CASCADE`).

Every one of these first runs `findByIdAndUserId(id, currentUser)`. If the
document belongs to someone else, the answer is **404**, exactly as if it didn't
exist. So nobody can probe other people's document ids.

---

## 5. How the frontend is connected

| File | Role |
|---|---|
| `frontend/.env.development.local` (yours, git-ignored) | `VITE_API_MOCKING=false`: use the real backend instead of the fake mock API |
| `frontend/vite.config.ts` | The dev server forwards `/api/*` to `http://localhost:8080`, so no CORS setup is needed |
| `frontend/src/lib/features.ts` | `BACKEND_FEATURES`: which parts the real backend already has. Menus and pages for the rest are hidden, and `/dashboard` redirects to `/documents` |
| `frontend/src/lib/api-client.ts` | Adds the token to every request; turns error JSON into messages |

To see the old demo with fake data again, set `VITE_API_MOCKING=true` in
`.env.development.local` and restart `npm run dev`.

When a later step adds a feature to the backend, we add its name to
`BACKEND_FEATURES` and its pages appear.

---

## 6. Run it

1. **Backend**: in IntelliJ, open the `bill-locker` folder and run **Backend** (top
   right). It reads `backend/.env`. Wait for `Started BillLockerApplication`.
2. **Frontend**: in a terminal in `frontend/`, run `npm run dev` and open
   http://localhost:5173. If it was already running, restart it so it picks up
   `.env.development.local`.
3. Register, upload a PDF or photo, open it, delete it. Watch the rows appear
   and disappear in pgAdmin.

### Try the API by hand (optional)

```bash
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d "{\"name\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"Passw0rd1\"}"
```
Copy the `token` from the answer, then:
```bash
curl -H "Authorization: Bearer PASTE_TOKEN_HERE" http://localhost:8080/api/documents
```
Without the header you get `401`. That's the security filter at work.

---

## 7. Tests

`backend/src/test/java/project/bill_locker/` has **17 tests**. They run against
a throwaway PostgreSQL in Docker, never your database. Docker Desktop must be
running. Run them from `backend/` with `./mvnw test`, or in IntelliJ: right-click
`src/test/java` → *Run 'All Tests'*.

- `AuthApiTests` covers:
  - registering and lower-casing the email
  - BCrypt storage
  - duplicate emails
  - form validation
  - right and wrong passwords
  - missing and forged tokens
  - an old token not blocking login
  - renaming
- `DocumentApiTests` covers:
  - upload → list → view → download (same bytes) → delete
  - file type by content
  - empty or missing file
  - **users can't see each other's documents**
  - filters
  - login required
  - file-name cleaning

---

## 8. Not in this step (on purpose)

- **Reading documents** (OCR + AI extraction) is the next step. Documents stay `UPLOADED`.
- **Refresh tokens:** after 24 h you log in again.
- **Rate limiting on login:** there is no limit yet on how many passwords one person
  can try.
- **Object storage (MinIO/S3):** files live in PostgreSQL for now. `DocumentFile` is
  the only class that would change.
- **Products, warranties, dashboard, notifications, AI chat, Gmail:** the designs are
  on branch `step-2-database`.
