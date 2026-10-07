# Task 3 — Gmail Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect several Gmail accounts (read-only), scan them for emails with bill attachments, let the user Import or Ignore each file, and read imported files with Gemini in the background.

**Architecture:** The database is the job queue: rows are marked "scan queued", "importing" or "queued for reading", and three `@Scheduled` workers (every 3 s) do the slow work outside DB transactions, then store results in short transactions. Google is behind a `GoogleApi` interface (real: `GoogleRestApi` over `RestClient`; tests: `FakeGoogleApi`), exactly like `DetailExtractor`/`FakeDetailExtractor`.

**Tech Stack:** Spring Boot 4.1.1 / Java 21 / Spring Data JPA / PostgreSQL / `RestClient`; React 19 + TS 5.9 + Tailwind 4 + lucide-react. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-07-task-3-gmail-import-design.md` (read it with this plan).

## Global Constraints

- No new Maven or npm dependencies. No Flyway/migration scripts: `ddl-auto=update` only, so only **new tables** and **new nullable columns**; never add values to existing enum columns (`documents.status`, `document_type` have CHECK constraints).
- **Never commit, merge or push** (user rule). Each task ends with a checkpoint, not a commit.
- Conventions (CLAUDE.md): controller → service (`@Transactional`) → repository; records in/out, entities never leave the backend; `ApiException(status, CODE, message[, fieldErrors])`; user id only from `CurrentUser.id(jwt)`; lookups `findByIdAndUserId` (other user's id → 404); entities extend `BaseEntity`; `@Enumerated(STRING)`; `@OnDelete`; constraint names `fk_/uk_/idx_`; short comments that say *why*.
- Google and Gemini are **never** called in tests. Workers never run on a timer in tests (`app.workers.enabled=false`); tests call `runOnce()`.
- Backend build while IntelliJ runs the app: `./mvnw test -Dmaven.compiler.useIncrementalCompilation=false` (from `backend/`). Tests need Docker Desktop running.
- Frontend: Node 20.18, keep Vite 6 / TS 5.9; `npm run typecheck` and `npm run build` must pass. All HTTP in `lib/api.ts`-based modules. Status colours always with icon + label. Never render API text as HTML.
- Scope `https://www.googleapis.com/auth/gmail.readonly` only. Tokens never logged, never sent to the browser.
- Redirect URI default: `http://localhost:8080/api/integrations/gmail/callback`.
- Files: PDF/JPEG/PNG/WebP, max 10 MB (`DocumentService.MAX_FILE_BYTES`). Scan: newest **200** emails max. Import request: 1–100 file ids.

## Review Focus

1. **Inline images in HTML emails** (logos, signatures: `image001.png`, a few KB, often no filename or `Content-Disposition: inline`) — must not show up as importable files. → Task 6 test `inlineLogosAreNotOffered`.
2. **Import clicked twice** (double click, two tabs) — must create **one** document. → Task 7 test `importingTwiceCreatesOneDocument`.
3. **Disconnect while a scan is running** — the finishing scan must not crash or bring the account back. → Task 6 test `disconnectDuringScanDropsTheResult`.
4. **Document deleted (or saved by the user) while queued for reading** — the read worker must skip it, never resurrect or overwrite it. → Task 1 tests `deletedWhileQueuedIsSkipped`, `savedWhileQueuedKeepsUserDetails`.
5. **Email deleted from Gmail between scan and import** — the file becomes `FAILED` with a clear reason, other files still import. → Task 7 test `fileGoneFromGmailFails`.

---

## File map

Backend (`backend/src/main/java/project/bill_locker/`):

| File | Responsibility |
|---|---|
| `common/WorkerSchedule.java` | `@EnableScheduling` + one `@Scheduled(fixedDelay = 3000)` per worker, only when `app.workers.enabled=true` |
| `document/Document.java` (modify) | `readQueuedAt`, `readError`, `sourceGmail` + methods |
| `document/DocumentService.java` (modify) | `createFromBytes`, `readNextQueued` |
| `document/DocumentReadWorker.java` | `runOnce()` → `DocumentService.readNextQueued()` |
| `document/DocumentDetail.java`, `DocumentSummary.java` (modify) | `readQueued`, `readError`, `sourceGmail` |
| `gmail/GmailProperties.java` | `app.gmail.*` + `isConfigured()` |
| `gmail/GmailConfig.java` | logs which key is missing at start-up |
| `gmail/TokenCipher.java` | AES-256-GCM for refresh tokens |
| `gmail/GoogleApi.java` | interface + records `Tokens`, `Email`, `Attachment` + `GoogleApiException` |
| `gmail/GoogleRestApi.java` | real Google over `RestClient` |
| `gmail/GmailAccount.java`, `GmailScanStatus.java`, `GmailAccountRepository.java` | accounts |
| `gmail/GmailConnectState.java`, `GmailConnectStateRepository.java` | OAuth state + PKCE |
| `gmail/GmailEmail.java`, `EmailKind.java`, `GmailEmailRepository.java` | found emails |
| `gmail/GmailFile.java`, `GmailFileStatus.java`, `GmailFileRepository.java` | found files |
| `gmail/EmailClassifier.java` | keyword rules → `Optional<EmailKind>` (empty = drop) |
| `gmail/GmailViews.java` | response records: `GmailOverview`, `GmailAccountView`, `GmailEmailView`, `GmailFileView`, `ConnectResponse`; request records `ScanRequest`, `FileIdsRequest`, enum `ScanRange` |
| `gmail/GmailConnectService.java` | connect + callback + disconnect |
| `gmail/GmailService.java` | overview, scan queue, email list, import/ignore/restore, worker claims + results |
| `gmail/GmailScanWorker.java`, `gmail/GmailImportWorker.java` | the slow Google calls |
| `gmail/GmailController.java` | `/api/integrations/gmail/**` |
| `security/SecurityConfig.java` (modify) | `GET /api/integrations/gmail/callback` public |

Tests (`backend/src/test/java/project/bill_locker/`): `FakeGoogleApi.java`, `ApiTest.java` (modify), `document/DocumentReadWorkerTests.java`, `gmail/TokenCipherTests.java`, `gmail/GoogleRestApiTests.java`, `gmail/GmailApiTestBase.java`, `gmail/GmailConnectApiTests.java`, `gmail/EmailClassifierTests.java`, `gmail/GmailScanTests.java`, `gmail/GmailImportTests.java`.

Frontend (`frontend/src/`): `lib/documents.ts`, `components/documents/StatusBadge.tsx`, `pages/DocumentsPage.tsx`, `pages/DocumentPage.tsx` (modify); `lib/gmail.ts`, `lib/usePolling.ts`, `pages/GmailPage.tsx`, `components/gmail/AccountCard.tsx`, `components/gmail/EmailCard.tsx`, `components/gmail/ScanMenu.tsx`, `App.tsx`, `components/AppHeader.tsx` (modify).

Docs: `docs/task-3-gmail.md`, `docs/api-contract.md`, `backend/.env.example`, `CLAUDE.md`.

---

### Task 1: Background AI reading of queued documents

**Files:**
- Modify: `document/Document.java`, `document/DocumentService.java`, `document/DocumentDetail.java`, `document/DocumentSummary.java`, `document/DocumentRepository.java`, `src/main/resources/application.properties`, `src/test/java/project/bill_locker/ApiTest.java`
- Create: `document/DocumentReadWorker.java`, `common/WorkerSchedule.java`
- Test: `src/test/java/project/bill_locker/document/DocumentReadWorkerTests.java`

**Interfaces:**
- Produces:
  - `Document`: columns `read_queued_at` (`Instant readQueuedAt`, index `idx_documents_read_queued`), `read_error` (`String`, 500), `source_gmail` (`String`, 254). Constructor overload `Document(User, String fileName, String contentType, long sizeBytes, String sourceGmail)`. Package-private: `void queueForReading()` (sets `readQueuedAt = Instant.now()`, `readError = null`), `void readFailed(String message)` (`readQueuedAt = null`, `readError = message`). `replaceDetails(...)` now also sets `readQueuedAt = null`, `readError = null` (so manual extract/save take a document out of the queue).
  - `DocumentService.createFromBytes(UUID userId, String fileName, byte[] bytes, String sourceGmail): DocumentDetail` — `@Transactional`; empty → `FILE_EMPTY`, > `MAX_FILE_BYTES` → `FILE_TOO_LARGE`, `FileType.detect` empty → `INVALID_FILE_TYPE` (same `ApiException`s as `upload`); stores document + file and calls `queueForReading()`. `upload(...)` delegates to the same private create code (with `sourceGmail = null`, not queued).
  - `DocumentService.readNextQueued(): boolean` — not `@Transactional`; returns `false` when nothing is queued.
  - `DocumentRepository.findFirstByReadQueuedAtIsNotNullOrderByReadQueuedAtAsc(): Optional<Document>`
  - `DocumentDetail` / `DocumentSummary` gain `boolean readQueued`, `String readError`, `String sourceGmail` (append at the end of the record components).
  - `DocumentReadWorker` (`@Component`): `public boolean runOnce()`.
  - `WorkerSchedule` (`@Configuration @EnableScheduling @ConditionalOnProperty(name = "app.workers.enabled", havingValue = "true", matchIfMissing = true)`) — later tasks add their worker calls here.
  - `ApiTest`: `@SpringBootTest(properties = "app.workers.enabled=false")`.

- [ ] **Step 1: Write the failing tests** in `DocumentReadWorkerTests extends DocumentApiTestBase` (autowire `DocumentService`, `DocumentReadWorker`, `FakeDetailExtractor`; `@AfterEach fake.reset()`). Queue a document with `documentService.createFromBytes(userId, "bill.pdf", pdfBytes(), "me@gmail.com")` (get `userId` from `GET /api/auth/me`).

```java
@Test void queuedDocumentIsReadByTheWorker()        // GET shows readQueued=true, sourceGmail="me@gmail.com"; runOnce() → true;
                                                     // GET: status EXTRACTED, readQueued=false, sellerName "Croma"; runOnce() → false
@Test void failedReadLeavesItUploadedWithReason()   // fake.willFail(new ExtractionException(FAILED, "Gemini is busy right now."));
                                                     // runOnce(); GET: status UPLOADED, readQueued=false, readError "Gemini is busy right now."
@Test void aiNotConfiguredIsReportedAsReadError()   // willFail(NOT_CONFIGURED,…) → readError contains "AI reading is not set up"
@Test void deletedWhileQueuedIsSkipped()            // DELETE the document; runOnce() → false, no exception
@Test void savedWhileQueuedKeepsUserDetails()       // PUT details (sellerName "Mine") while queued → readQueued=false;
                                                     // runOnce() → false; GET: sellerName "Mine", status SAVED
@Test void listShowsReadingState()                  // GET /api/documents: $[0].readQueued == true
@Test void createFromBytesChecksTheFileLikeUpload() // text bytes → ApiException code INVALID_FILE_TYPE; 10 MB + 1 → FILE_TOO_LARGE
```

- [ ] **Step 2: Run, expect FAIL** (compile errors: `createFromBytes`, `DocumentReadWorker` missing).
Run: `./mvnw test -Dtest=DocumentReadWorkerTests -Dmaven.compiler.useIncrementalCompilation=false`

- [ ] **Step 3: Implement.** `readNextQueued()`:

```text
tx1: doc = findFirstByReadQueuedAt…; none → return false
     remember id, userId, queuedAt = doc.readQueuedAt; load file bytes
no tx: details = withWarrantyEndDates(extractor.extract(bytes, type))
       catch ExtractionException e → message = NOT_CONFIGURED ? the same "AI reading is not set up…" text as extract() : e.getMessage()
tx2: doc = documents.findById(id); if absent or !queuedAt.equals(doc.readQueuedAt) → return true (deleted / user took over)
     success → doc.replaceDetails(details, EXTRACTED); failure → doc.readFailed(message)
return true
```
Catch any other `RuntimeException` from the extractor too → `readFailed("Reading failed. Press Read with AI to try again.")` and log it, so one bad file never blocks the queue. Add `app.workers.enabled=true` to `application.properties` with a one-line comment. `WorkerSchedule` gets `@Scheduled(fixedDelay = 3000, initialDelay = 10000) void readDocuments() { reader.runOnce(); }`.

- [ ] **Step 4: Run the new tests and the whole suite, expect PASS.**
Run: `./mvnw test -Dmaven.compiler.useIncrementalCompilation=false` → `BUILD SUCCESS`, 0 failures.

- [ ] **Step 5: Checkpoint** — no commit (user rule). Note the new columns in your report.

---

### Task 2: Gmail settings, token encryption, `GoogleApi` and the fake

**Files:**
- Create: `gmail/GmailProperties.java`, `gmail/GmailConfig.java`, `gmail/TokenCipher.java`, `gmail/GoogleApi.java`, `src/test/java/project/bill_locker/FakeGoogleApi.java`
- Modify: `application.properties`, `ApiTest.java`
- Test: `gmail/TokenCipherTests.java`, `gmail/GmailPropertiesTests.java`

**Interfaces:**
- Produces:
  - `GmailProperties` — `@ConfigurationProperties("app.gmail") record GmailProperties(String clientId, String clientSecret, String tokenKey, String redirectUri)`; `boolean isConfigured()` = all four non-blank **and** `tokenKey` base64-decodes to 32 bytes. Properties:
    `app.gmail.client-id=${GOOGLE_CLIENT_ID:}`, `app.gmail.client-secret=${GOOGLE_CLIENT_SECRET:}`, `app.gmail.token-key=${GMAIL_TOKEN_KEY:}`, `app.gmail.redirect-uri=${GMAIL_REDIRECT_URI:http://localhost:8080/api/integrations/gmail/callback}`.
  - `GmailConfig` (`@Configuration @EnableConfigurationProperties(GmailProperties.class)`): on `ApplicationReadyEvent` logs WARN `Gmail import is off: <KEY> is not set` (or `GMAIL_TOKEN_KEY must be 32 bytes in base64 (openssl rand -base64 32)`) when not configured; INFO `Gmail import is on (redirect URI …)` otherwise.
  - `TokenCipher` (`@Component`, built from `GmailProperties`): `byte[] encrypt(String plain)`, `String decrypt(byte[] cipher)` — AES/GCM/NoPadding, 12-byte random nonce prepended, 128-bit tag. Throws `IllegalStateException("Gmail import is not configured")` when the key is unusable. Also a package-private constructor `TokenCipher(byte[] key)` for unit tests.
  - `GoogleApi` interface, exactly these members:
    ```java
    String authorizationUrl(String state, String codeChallenge);
    Tokens exchangeCode(String code, String codeVerifier);
    String accessToken(String refreshToken);
    String emailAddress(String accessToken);
    List<String> searchMessages(String accessToken, String query, int max);
    Email message(String accessToken, String messageId);
    byte[] attachment(String accessToken, String messageId, String attachmentId);
    void revoke(String refreshToken);
    record Tokens(String accessToken, String refreshToken) {}            // refreshToken may be null
    record Email(String id, String from, String subject, String snippet, Instant receivedAt, List<Attachment> attachments) {}
    record Attachment(String partId, String attachmentId, String fileName, String contentType, long sizeBytes, boolean inline) {}
    class GoogleApiException extends RuntimeException { GoogleApiException(String msg, Kind kind, Throwable cause); Kind kind(); }
    enum Kind { REVOKED, NOT_FOUND, UNAVAILABLE }   // nested in GoogleApiException
    ```
  - `FakeGoogleApi implements GoogleApi` (test, with `@TestConfiguration Config` + `@Primary` bean like `FakeDetailExtractor`):
    - `authorizationUrl` → `"https://fake.google/auth?state=" + state + "&code_challenge=" + challenge`
    - `void willAuthorize(String code, String address)`; `exchangeCode` for an unknown code throws `REVOKED`; returns `Tokens("access-"+address, "refresh-"+address)`
    - `accessToken("refresh-"+a)` → `"access-"+a`; `emailAddress("access-"+a)` → `a`
    - `void addEmail(String address, Email email, Map<String, byte[]> filesByAttachmentId)`; `void removeEmail(String address, String id)`
    - `searchMessages` returns the mailbox ids newest first, records `String lastQuery()`
    - `message`/`attachment` for an unknown id throw `NOT_FOUND`
    - `void failNext(GoogleApiException e)` (next call of any method throws it); `List<String> revoked()`; `void reset()`
  - `ApiTest` `@SpringBootTest(properties = {"app.workers.enabled=false", "app.gmail.client-id=test-client", "app.gmail.client-secret=test-secret", "app.gmail.token-key=" + TEST_TOKEN_KEY})` with `TEST_TOKEN_KEY` = a fixed base64 32-byte string; add `FakeGoogleApi.Config.class` to `@Import`.

- [ ] **Step 1: Write the failing tests** `TokenCipherTests` (plain unit test, no Spring):

```java
@Test void roundTrip()          // decrypt(encrypt("refresh-abc")) equals "refresh-abc"
@Test void sameTextEncryptsDifferently()   // two encryptions differ (random nonce)
@Test void tamperedCipherFails()           // flip last byte → decrypt throws (AEADBadTagException wrapped is fine)
```

and `GmailPropertiesTests` (plain unit test):

```java
@Test void configuredWithAllKeys()        // ("id","secret", base64 of 32 bytes, "http://x/cb").isConfigured() → true
@Test void blankKeyIsNotConfigured()      // tokenKey "" → false; clientId "" → false
@Test void shortKeyIsNotConfigured()      // base64 of 16 bytes → false; "not base64!" → false (no exception)
```

- [ ] **Step 2: Run, expect FAIL** (classes missing). Run: `./mvnw test -Dtest=TokenCipherTests,GmailPropertiesTests -Dmaven.compiler.useIncrementalCompilation=false`
- [ ] **Step 3: Implement** the files above. (The real `GoogleApi` bean comes in Task 3; nothing injects `GoogleApi` before Task 4.)
- [ ] **Step 4: Run the full suite, expect PASS.**
- [ ] **Step 5: Checkpoint.**

---

### Task 3: `GoogleRestApi` — the real Google calls

**Files:**
- Create: `gmail/GoogleRestApi.java`
- Test: `gmail/GoogleRestApiTests.java` (style of `GeminiDetailExtractorTests`: `MockRestServiceServer`, nothing leaves the computer)

**Interfaces:**
- Consumes: `GoogleApi`, `GmailProperties` (Task 2)
- Produces: `@Component GoogleRestApi implements GoogleApi`, constructor `GoogleRestApi(RestClient.Builder builder, GmailProperties properties)` (tests bind a `MockRestServiceServer` to the builder). Endpoints:
  - auth: `https://accounts.google.com/o/oauth2/v2/auth` with `client_id, redirect_uri, response_type=code, scope=https://www.googleapis.com/auth/gmail.readonly, access_type=offline, prompt=consent select_account, include_granted_scopes=true, state, code_challenge, code_challenge_method=S256` (URL-encoded)
  - token: `POST https://oauth2.googleapis.com/token` form (`grant_type=authorization_code` + `code_verifier`, or `grant_type=refresh_token`)
  - `GET https://gmail.googleapis.com/gmail/v1/users/me/profile` → `emailAddress`
  - `GET …/users/me/messages?q=…&maxResults=…[&pageToken=…]` → `messages[].id`, follow `nextPageToken` until `max`
  - `GET …/users/me/messages/{id}?format=full` → headers `From`, `Subject`, `internalDate` (ms), `snippet`; walk `payload.parts` recursively; a part is an attachment when it has `body.attachmentId` **and** a non-blank `filename`; `inline` = its `Content-Disposition` header starts with `inline`
  - `GET …/messages/{id}/attachments/{aid}` → `data` base64url
  - `POST https://oauth2.googleapis.com/revoke?token=…`
  - Errors: token endpoint `400 invalid_grant` or any `401`/`403` → `REVOKED`; `404` → `NOT_FOUND`; other HTTP errors, timeouts, no connection → `UNAVAILABLE`. Never put tokens in log lines or exception messages.

- [ ] **Step 1: Write failing tests:**

```java
@Test void authorizationUrlAsksForReadOnlyAndAccountChoice()  // decoded query: scope "https://www.googleapis.com/auth/gmail.readonly",
                                                              // prompt "consent select_account", code_challenge_method "S256", access_type "offline"
@Test void exchangeCodeSendsVerifier()      // expect POST token with body containing "code_verifier=v1" → Tokens("a","r")
@Test void invalidGrantMeansRevoked()       // 400 {"error":"invalid_grant"} on refresh → GoogleApiException kind REVOKED
@Test void searchFollowsPages()             // page1 {messages:[{id:"1"},{id:"2"}],nextPageToken:"p2"}, page2 {messages:[{id:"3"}]} → ["1","2","3"]
@Test void searchStopsAtMax()               // max 2 → ["1","2"], no second request
@Test void messageReadsHeadersAndNestedAttachments()   // multipart/mixed > multipart/alternative + application/pdf part (filename "Invoice.pdf",
                                                       // attachmentId "att1", size 1234) + inline image part → 2 attachments, the image inline=true;
                                                       // from/subject/snippet/receivedAt = Instant.ofEpochMilli(internalDate)
@Test void attachmentDecodesBase64Url()     // data "JVBERi0x" → bytes "%PDF-1"
@Test void notFoundMessage()                // 404 → kind NOT_FOUND
@Test void serverErrorIsUnavailable()       // 503 → kind UNAVAILABLE
```

- [ ] **Step 2: Run, expect FAIL.** `./mvnw test -Dtest=GoogleRestApiTests -Dmaven.compiler.useIncrementalCompilation=false`
- [ ] **Step 3: Implement `GoogleRestApi`** (Jackson `JsonNode` reading like `GeminiDetailExtractor`; read timeout 30 s via `JdkClientHttpRequestFactory` in the production constructor path).
- [ ] **Step 4: Run, expect PASS** (whole suite).
- [ ] **Step 5: Checkpoint.**

---

### Task 4: Accounts, connect, callback, overview, disconnect

**Files:**
- Create: `gmail/GmailAccount.java`, `GmailScanStatus.java`, `GmailAccountRepository.java`, `GmailConnectState.java`, `GmailConnectStateRepository.java`, `GmailEmail.java`, `EmailKind.java`, `GmailEmailRepository.java`, `GmailFile.java`, `GmailFileStatus.java`, `GmailFileRepository.java`, `GmailViews.java`, `GmailConnectService.java`, `GmailService.java` (overview part), `GmailController.java`
- Modify: `security/SecurityConfig.java`
- Test: `gmail/GmailApiTestBase.java`, `gmail/GmailConnectApiTests.java`

**Interfaces:**
- Consumes: `GoogleApi`, `TokenCipher`, `GmailProperties` (Tasks 2–3); `UserRepository`; the `Document` entity (only as the target of `GmailFile.document`).
- Produces:
  - Entities/tables exactly as spec §3. `GmailFile.document`: `@ManyToOne(fetch = LAZY) @JoinColumn(name = "document_id", foreignKey = @ForeignKey(name = "fk_gmail_files_document")) @OnDelete(action = SET_NULL) Document document`.
  - Enums: `GmailScanStatus { IDLE, QUEUED, SCANNING, ERROR }`, `EmailKind { INVOICE, WARRANTY, SERVICE, RECEIPT, ORDER, UNSURE }`, `GmailFileStatus { NEW, IGNORED, IMPORTING, IMPORTED, FAILED }`.
  - `GmailAccount` methods: `queueScan(LocalDate since)` (`QUEUED`, `scanSince`, `lastError = null`), `startScan()`, `finishScan()` (`IDLE`, `lastScannedAt = now`), `failScan(String reason)` (`ERROR`), `updateToken(byte[] cipher)`.
  - `enum ScanRange { SIX_MONTHS(6), ONE_YEAR(12), TWO_YEARS(24), FIVE_YEARS(60); LocalDate since(LocalDate today) }` (in `GmailViews.java`).
  - Records (in `GmailViews.java`): `ConnectResponse(String authorizationUrl)`; `GmailOverview(boolean configured, List<GmailAccountView> accounts, Counts counts)` with `Counts(long toReview, long ignored, long imported)`; `GmailAccountView(UUID id, String email, GmailScanStatus scanStatus, Instant lastScannedAt, String lastError, Instant connectedAt)`; `GmailEmailView(UUID id, String accountEmail, String fromName, String fromEmail, String subject, String snippet, Instant receivedAt, EmailKind kind, List<GmailFileView> files)`; `GmailFileView(UUID id, String fileName, String contentType, long sizeBytes, GmailFileStatus status, String error, DocumentRef document)` with `DocumentRef(UUID id, DocumentStatus status, boolean readQueued)`; `ScanRequest(@NotNull ScanRange range)`; `FileIdsRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> fileIds)`.
  - `GmailConnectService`: `ConnectResponse connect(UUID userId)`; `String callback(String code, String state, String error)` → returns the full redirect URL; `void disconnect(UUID userId, UUID accountId)`.
  - `GmailService`: `GmailOverview overview(UUID userId)`; `void requireConfigured()` → `ApiException(503, "GMAIL_NOT_CONFIGURED", "Gmail import isn't set up on this server yet.")`; `GmailAccount findAccount(UUID userId, UUID id)` → 404 `GMAIL_ACCOUNT_NOT_FOUND` "That Gmail account was not found.".
  - Controller `@RequestMapping("/api/integrations/gmail")`: `GET ""` → overview; `POST /connect`; `GET /callback` → `ResponseEntity` 302 `Location`; `DELETE /accounts/{id}` → 204. `SecurityConfig`: `.requestMatchers(HttpMethod.GET, "/api/integrations/gmail/callback").permitAll()`.
  - `GmailApiTestBase extends ApiTest`: `@Autowired FakeGoogleApi google` (+ `@AfterEach google.reset()`); helper `String connect(String token, String address)` — POST `/connect`, read `state` from `authorizationUrl`, `google.willAuthorize("code-"+address, address)`, GET `/callback?code=code-<address>&state=<state>` (no auth header), expect 302, return the account id from `GET ""`.

- [ ] **Step 1: Write failing tests** in `GmailConnectApiTests extends GmailApiTestBase`:

```java
@Test void connectGivesGoogleUrlWithStateAndPkce()   // authorizationUrl contains "state=" and "code_challenge="
@Test void callbackConnectsAndRedirects()            // 302 Location "http://localhost:5173/gmail?connected=a%40gmail.com" (URL-encoded);
                                                     // GET "" → accounts[0].email "a@gmail.com", scanStatus "QUEUED", configured true
@Test void callbackNeedsNoLoginToken()               // (covered by helper: no Authorization header) — assert not 401
@Test void stateWorksOnce()                          // reuse the same state → Location ends with "?error=expired"
@Test void expiredStateIsRejected()                  // set expiresAt in the past via repository → "?error=expired"
@Test void unknownStateIsRejected()                  // state "nope" → "?error=expired"
@Test void googleDeniedAccess()                      // /callback?error=access_denied&state=<state> → "?error=denied"
@Test void twoAccountsForOneUser()                   // connect a@ and b@ → 2 accounts
@Test void sameAddressAgainUpdatesTheToken()         // connect a@ twice → 1 account
@Test void refreshTokenIsStoredEncrypted()           // repository: refreshTokenCiphertext does not contain bytes of "refresh-a@gmail.com"
@Test void disconnectRevokesAndDeletes()             // DELETE → 204; accounts empty; google.revoked() contains "refresh-a@gmail.com"
@Test void otherUsersAccountIs404()                  // user B DELETE A's account → 404 GMAIL_ACCOUNT_NOT_FOUND
@Test void googleUnavailableOnCallback()             // google.failNext(UNAVAILABLE) → "?error=failed"
```

- [ ] **Step 2: Run, expect FAIL.** `./mvnw test -Dtest=GmailConnectApiTests -Dmaven.compiler.useIncrementalCompilation=false`
- [ ] **Step 3: Implement.** Connect: `SecureRandom` 32 bytes → base64url state and verifier; challenge = base64url(SHA-256(verifier)) without padding; store `sha256Hex(state)`; delete the user's expired states. Callback: Google calls outside transactions (`TransactionTemplate` like `DocumentService.extract`); unknown/expired/used → `?error=expired`; `error` param present → `?error=denied`; `GoogleApiException` or null refresh token → `?error=failed`; upsert by `(user, email)`, `queueScan(ScanRange.ONE_YEAR.since(today))`. Redirect base = `app.frontend-url`. Disconnect: load (404 if not owned), revoke outside the transaction ignoring `GoogleApiException`, then delete (cascade removes emails/files). All endpoints except `GET ""` and `/callback` call `requireConfigured()`; `/callback` when not configured redirects `?error=failed`.
- [ ] **Step 4: Run, expect PASS** (whole suite).
- [ ] **Step 5: Checkpoint.**

---

### Task 5: `EmailClassifier`

**Files:**
- Create: `gmail/EmailClassifier.java`
- Test: `gmail/EmailClassifierTests.java` (plain unit test)

**Interfaces:**
- Produces: `@Component EmailClassifier` with `Optional<EmailKind> classify(String subject, String snippet, List<String> fileNames)`; empty = drop. Rules exactly as spec §4.5, in this order: junk-without-invoice-file → empty; WARRANTY; SERVICE; INVOICE; RECEIPT; ORDER; else UNSURE. Whole words (`\b…\b`), lower-case, file names with `[_.\-]` → space before matching.

- [ ] **Step 1: Write failing tests** (one `@ParameterizedTest @CsvSource` is fine):

| subject | fileNames | expected |
|---|---|---|
| `Your Croma tax invoice` | `INV-22.pdf` | `INVOICE` |
| `Order #123` | `Invoice_123.pdf` | `INVOICE` |
| `Warranty card for your AC` | `card.jpg` | `WARRANTY` |
| `Extended guarantee certificate` | `cert.pdf` | `WARRANTY` |
| `Service report – washing machine` | `report.pdf` | `SERVICE` |
| `Payment received` | `r.pdf` | `RECEIPT` |
| `Your order has shipped` | `slip.pdf` | `ORDER` |
| `Documents attached` | `scan.pdf` | `UNSURE` |
| `Festive sale – 50% off` | `catalogue.pdf` | *(empty)* |
| `Your account statement` | `invoice_jan.pdf` | `INVOICE` (invoice file beats junk) |
| `Billion-dollar ideas newsletter` | `x.pdf` | *(empty)* ("bill" ≠ "billion") |

- [ ] **Step 2: Run, expect FAIL.** `./mvnw test -Dtest=EmailClassifierTests -Dmaven.compiler.useIncrementalCompilation=false`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Checkpoint.**

---

### Task 6: Scanning

**Files:**
- Create: `gmail/GmailScanWorker.java`
- Modify: `gmail/GmailService.java`, `gmail/GmailController.java`, `common/WorkerSchedule.java`
- Test: `gmail/GmailScanTests.java`

**Interfaces:**
- Consumes: Tasks 2–5.
- Produces:
  - `POST /accounts/{id}/scan` body `ScanRequest` → `GmailAccountView` (`QUEUED`); already `QUEUED`/`SCANNING` → `409 GMAIL_SCAN_RUNNING` "A scan is already running for this account.".
  - `GmailService.queueScan(UUID userId, UUID accountId, ScanRange range): GmailAccountView`
  - `GmailService.claimNextScan(): Optional<ScanJob>` — `record ScanJob(UUID accountId, String refreshToken, LocalDate since, Set<String> knownMessageIds)`; oldest `QUEUED` → `SCANNING`, decrypts the token.
  - `GmailService.saveScan(UUID accountId, List<FoundEmail> emails)` — `record FoundEmail(GoogleApi.Email email, EmailKind kind, List<GoogleApi.Attachment> files)`; account gone → do nothing; skips message ids already stored (unique constraint safety); `finishScan()`.
  - `GmailService.failScan(UUID accountId, String reason)` (account gone → nothing).
  - `GmailService.resetStuckScans()` on `ApplicationReadyEvent`: `SCANNING` → `QUEUED`.
  - `GmailScanWorker.runOnce(): boolean`; `static String query(LocalDate since)` = `after:YYYY/MM/DD has:attachment (invoice OR receipt OR bill OR warranty OR guarantee OR service OR "order confirmation" OR "tax invoice")`; `static final int MAX_EMAILS = 200`.
  - File filter (worker): keep attachment when **not** `inline`, `fileName` not blank, `sizeBytes <= 10 MB`, type is PDF/JPEG/PNG/WebP by `contentType` (`application/pdf`, `image/jpeg`, `image/png`, `image/webp`) **or** by extension (`.pdf .jpg .jpeg .png .webp`, for `application/octet-stream`), and images are ≥ 20 KB (logos/signatures are smaller). Email with no kept file → skipped.
  - Error texts (spec §4.5): REVOKED → "Gmail access was removed. Connect this account again."; UNAVAILABLE/NOT_FOUND → "Gmail could not be reached. Try Scan again later."; other exceptions → "The scan failed. Please try again." (logged with stack trace).
  - `WorkerSchedule`: `@Scheduled(fixedDelay = 3000, initialDelay = 10000) void scanGmail() { scanWorker.runOnce(); }`.

- [ ] **Step 1: Write failing tests** in `GmailScanTests extends GmailApiTestBase` (connect → scan is queued; call `scanWorker.runOnce()`; read with `GET /emails?view=TO_REVIEW` — that endpoint comes in Task 7, so here assert through the repositories `GmailEmailRepository` / `GmailFileRepository` and `GET ""`):

```java
@Test void scanSavesEmailsWithBillFiles()        // mailbox: invoice email (pdf), newsletter (pdf), email with only .docx → 1 email, 1 file NEW, kind INVOICE;
                                                  // account IDLE, lastScannedAt set
@Test void secondScanAddsOnlyNewEmails()         // scan (1 email); add a 2nd email; POST scan; runOnce → exactly 2 email rows, first one not duplicated
@Test void scanRangeBecomesAfterDate()           // POST scan {range: TWO_YEARS} → google.lastQuery() starts with "after:" + today.minusMonths(24) formatted yyyy/MM/dd
@Test void inlineLogosAreNotOffered()            // email with invoice.pdf + inline image001.png (5 KB) + non-inline logo.png (3 KB) → only invoice.pdf
@Test void octetStreamPdfByExtensionIsKept()     // contentType application/octet-stream, name "bill.PDF" → kept
@Test void revokedAccessShowsError()             // failNext(REVOKED) → scanStatus ERROR, lastError "Gmail access was removed. Connect this account again."
@Test void scanWhileRunningIs409()               // after connect (QUEUED) POST scan → 409 GMAIL_SCAN_RUNNING
@Test void disconnectDuringScanDropsTheResult()  // claimNextScan(); DELETE account; saveScan(job.accountId(), …) → no exception, 0 emails, 0 accounts
@Test void stuckScanIsRequeued()                 // set SCANNING via repository; resetStuckScans() → QUEUED
@Test void badRangeIs400()                       // {"range":"TEN_YEARS"} → 400
```

- [ ] **Step 2: Run, expect FAIL.** `./mvnw test -Dtest=GmailScanTests -Dmaven.compiler.useIncrementalCompilation=false`
- [ ] **Step 3: Implement** (Google calls in the worker only; `GmailService` methods are short `@Transactional` methods).
- [ ] **Step 4: Run, expect PASS** (whole suite).
- [ ] **Step 5: Checkpoint.**

---

### Task 7: Review list, Import / Ignore / Restore, import worker

**Files:**
- Create: `gmail/GmailImportWorker.java`
- Modify: `gmail/GmailService.java`, `gmail/GmailController.java`, `common/WorkerSchedule.java`
- Test: `gmail/GmailImportTests.java`

**Interfaces:**
- Consumes: Task 1 `DocumentService.createFromBytes`, `DocumentReadWorker.runOnce`; Tasks 4–6.
- Produces:
  - `enum EmailView { TO_REVIEW, IGNORED, IMPORTED }` (in `GmailViews.java`) — statuses per spec §4.3.
  - `GET /emails?view=…` (default `TO_REVIEW`) → `List<GmailEmailView>` newest first; each email carries only its files in that view; emails with none are left out. `GmailOverview.counts` counts files per view.
  - `POST /files/import`, `/files/ignore`, `/files/restore` with `FileIdsRequest` → `List<GmailFileView>`. Any id not the user's → 404 `GMAIL_FILE_NOT_FOUND` "That file was not found." and nothing changes. Transitions exactly as spec §4.3 (import also accepts `IMPORTED` whose `document` is null); other statuses unchanged.
  - `GmailService.claimImports(int max): List<ImportJob>` — `record ImportJob(UUID fileId, UUID userId, String accountEmail, String refreshToken, String gmailMessageId, String partId, String fileName)`; oldest `IMPORTING` first, max 5.
  - `GmailService.importDone(UUID fileId, UUID documentId)` → `IMPORTED`; `importFailed(UUID fileId, String reason)` → `FAILED`; a file deleted meanwhile (account disconnected) → nothing.
  - `GmailImportWorker.runOnce(): int` (files handled). Per job: `accessToken` → `message(id)` → attachment with the same `partId` (missing → failed "This file is no longer in Gmail.") → `attachment(...)` → `documentService.createFromBytes(userId, fileName, bytes, accountEmail)`; `ApiException` from it → failed with its message ("Please upload a PDF, JPG, PNG or WebP file." reads oddly here → use "This file isn't a PDF or image Bill Locker can read." for `INVALID_FILE_TYPE`, keep others). `NOT_FOUND` → "This file is no longer in Gmail."; `REVOKED` → failed "Gmail access was removed. Connect this account again." + `failScan` on the account; `UNAVAILABLE` → leave it `IMPORTING` (retried next run) and stop this run.
  - `WorkerSchedule`: `@Scheduled(fixedDelay = 3000, initialDelay = 10000) void importGmail() { importWorker.runOnce(); }`.

- [ ] **Step 1: Write failing tests** in `GmailImportTests extends GmailApiTestBase` (helper: connect + add emails + `scanWorker.runOnce()`, then list):

```java
@Test void toReviewListsNewFilesNewestFirst()      // 2 emails → order by receivedAt desc; files[0].status "NEW"; counts.toReview 2
@Test void importCreatesQueuedDocument()           // import → files status IMPORTING; importWorker.runOnce() → 1;
                                                   // view=IMPORTED shows document.id, document.readQueued true;
                                                   // GET /api/documents/{id} sourceGmail "a@gmail.com", fileName "invoice.pdf"
@Test void importedDocumentIsReadInBackground()    // then documentReadWorker.runOnce() → document status EXTRACTED, readQueued false
@Test void importingTwiceCreatesOneDocument()      // POST import twice before runOnce → runOnce → exactly 1 document in GET /api/documents
@Test void fakePdfFails()                          // attachment bytes "hello" named x.pdf → FAILED, error "This file isn't a PDF or image Bill Locker can read."
@Test void fileGoneFromGmailFails()                // google.removeEmail(...) before runOnce → FAILED "This file is no longer in Gmail."; other email's file still IMPORTED
@Test void ignoreRestoreImport()                   // ignore → view=IGNORED has it, TO_REVIEW not; restore → NEW; ignore again; import from IGNORED works
@Test void ignoredFileIsNotOfferedAgainByScan()    // ignore; POST scan; runOnce → still 1 file, status IGNORED
@Test void failedFileCanBeRetried()                // FAILED → import → IMPORTING
@Test void deletedDocumentCanBeImportedAgain()     // import, runOnce, DELETE /api/documents/{id} → view=IMPORTED document null; import → IMPORTING
@Test void disconnectKeepsImportedDocuments()      // import, runOnce, DELETE account → GET /api/documents still has it; emails list empty
@Test void otherUsersFileIs404AndNothingChanges()  // user B imports [A's file] → 404 GMAIL_FILE_NOT_FOUND; A's file still NEW
@Test void emptyOrTooManyIdsIs400()                // [] → 400; 101 ids → 400
```

- [ ] **Step 2: Run, expect FAIL.** `./mvnw test -Dtest=GmailImportTests -Dmaven.compiler.useIncrementalCompilation=false`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the whole suite, expect PASS** → `BUILD SUCCESS`.
- [ ] **Step 5: Checkpoint.** Tell the user: restart the backend in IntelliJ (new tables are created on start-up).

---

### Task 8: Frontend — "Reading…" for queued documents

**Files:**
- Modify: `lib/documents.ts`, `components/documents/StatusBadge.tsx`, `pages/DocumentsPage.tsx`, `pages/DocumentPage.tsx`
- Create: `lib/usePolling.ts`

**Interfaces:**
- Consumes: Task 1 fields `readQueued: boolean`, `readError: string | null`, `sourceGmail: string | null` on `DocumentDetail` and `DocumentSummary`.
- Produces:
  - `usePolling(active: boolean, refresh: () => void, ms = 3000): void` — `setInterval` while `active`, cleared otherwise; also skips while `document.hidden`.
  - `StatusBadge({ status, reading }: { status: DocumentStatus; reading?: boolean })` — `reading` → `LoaderCircle` (spinning) + "Reading…", slate colours.

- [ ] **Step 1: Types + badge.** Add the three fields to both types; `StatusBadge` reading state.
- [ ] **Step 2: Documents list.** Rows pass `reading={d.readQueued}`; Gmail rows show a small `Mail` icon + "Gmail" next to the meta line (title = `sourceGmail`); poll `listDocuments` while any row `readQueued` (keep the existing filters' effect; polling just re-runs the same request).
- [ ] **Step 3: Document page.** While `readQueued`: overlay text "Reading your document with AI…" (reuse the existing overlay) and poll `getDocument` every 3 s; when it flips, `show(d)` and the info message "Details read by AI. Please check them, fix anything wrong, then save.". When `readError` and status `UPLOADED`: an error `Alert` with `readError` (the **Read with AI** button is already in the toolbar). Header shows "From Gmail · a@gmail.com" under the title when `sourceGmail`. Don't show the overlay while the user has unsaved edits (it can't happen: queued documents have no user edits yet, but guard with `!dirty`).
- [ ] **Step 4: Verify.** `npm run typecheck` → no output errors; `npm run build` → success. Browser: queue a document (Task 7 import, or `createFromBytes` via a Gmail import) and watch "Reading…" become "Needs review" without reloading.
- [ ] **Step 5: Checkpoint.**

---

### Task 9: Frontend — the Gmail page

**Files:**
- Create: `lib/gmail.ts`, `pages/GmailPage.tsx`, `components/gmail/AccountCard.tsx`, `components/gmail/ScanMenu.tsx`, `components/gmail/EmailCard.tsx`
- Modify: `App.tsx` (route `/gmail` inside `RequireAuth`), `components/AppHeader.tsx` (link "Gmail" between Documents and the right side)

**Interfaces:**
- Consumes: Task 4/6/7 endpoints and records (same field names in TS); `usePolling` (Task 8); `StatusBadge` (Task 8).
- Produces (`lib/gmail.ts`): types `GmailOverview`, `GmailAccount`, `GmailEmail`, `GmailFile`, `ScanRange`, `EmailView`, `EmailKind`, `GmailFileStatus`; labels `SCAN_RANGE_LABELS` (`SIX_MONTHS: 'Last 6 months'`, `ONE_YEAR: 'Last year'`, `TWO_YEARS: 'Last 2 years'`, `FIVE_YEARS: 'Last 5 years'`), `EMAIL_KIND_LABELS` (`INVOICE: 'Invoice'`, `WARRANTY: 'Warranty / guarantee'`, `SERVICE: 'Service receipt'`, `RECEIPT: 'Receipt'`, `ORDER: 'Order'`, `UNSURE: 'Might be a bill'`); functions `getGmail()`, `connectGmail()`, `scanAccount(id, range)`, `disconnectAccount(id)`, `listEmails(view)`, `importFiles(ids)`, `ignoreFiles(ids)`, `restoreFiles(ids)`; `CALLBACK_ERRORS` (`denied`: "You didn't allow access in Google, so nothing was connected.", `expired`: "That connection attempt expired. Please try again.", `failed`: "Google could not confirm the connection. Please try again.").

- [ ] **Step 1: `lib/gmail.ts`.**
- [ ] **Step 2: Page shell + accounts.** `GmailPage`: header "Gmail import" + one-line explanation ("Bill Locker looks for bills attached to your emails. Read-only: it never sends, changes or deletes mail."). Not configured → info panel naming `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GMAIL_TOKEN_KEY` and "see docs/task-3-gmail.md". `?connected=` → success `Alert` "Connected {email}. Scanning the last year…"; `?error=` → `CALLBACK_ERRORS`; then remove the query (`setSearchParams({}, { replace: true })`). Connect button → `connectGmail()` → `window.location.assign(authorizationUrl)`. `AccountCard`: address, status line (QUEUED/SCANNING: spinner "Scanning…"; ERROR: `CircleAlert` + `lastError`; IDLE: "Last scanned {relative time}" or "Not scanned yet"), `ScanMenu` (button "Scan" + menu of the four ranges; disabled while scanning), Disconnect (confirm: "Disconnect {email}? Bills you imported stay in Documents.").
- [ ] **Step 3: Found files.** Tabs To review / Ignored / Imported with counts from the overview. `EmailCard`: kind badge (icon + `EMAIL_KIND_LABELS`), sender (name or address), subject, date, "via {accountEmail}", snippet (one line, `truncate`). File rows: checkbox, `FileText`/`Image` icon, name, size (KB/MB), and per status the actions from spec §5 (Imported → "Open document" link to `/documents/{id}` + `StatusBadge` with `reading`; document null → "Deleted from Documents" + **Import again**; Failed → `CircleAlert` + error + **Retry** / **Ignore**; Importing → spinner "Importing…"). Sticky bottom bar when any box is ticked: **Import selected (n)** / **Ignore selected (n)** (Ignored tab: **Import selected** / **Restore selected**). Empty states: no accounts → "Connect Gmail to find bills in your inbox"; To review empty → "All caught up. Run a scan to look for new bills.".
- [ ] **Step 4: Polling.** `usePolling(active, reload)` where `active` = any account QUEUED/SCANNING, any file IMPORTING, or any imported file's `document.readQueued`. `reload` refreshes overview + the current tab.
- [ ] **Step 5: Verify.** `npm run typecheck`, `npm run build` pass. In the browser (backend running, real Google client in `.env`): connect an account, watch the scan, import one file, ignore one, see the import appear in Documents as "Reading…" → "Needs review". Check 375 px width: account cards stack, the action bar fits.
- [ ] **Step 6: Checkpoint.**

---

### Task 10: Docs

**Files:**
- Create: `docs/task-3-gmail.md`
- Modify: `docs/api-contract.md` (top "built so far" list + §13 rewritten for accounts/files/views, error codes `GMAIL_NOT_CONFIGURED`, `GMAIL_ACCOUNT_NOT_FOUND`, `GMAIL_FILE_NOT_FOUND`, `GMAIL_SCAN_RUNNING`), `backend/.env.example` (the three keys + `GMAIL_REDIRECT_URI` optional, with how to make the token key: `openssl rand -base64 32`), `CLAUDE.md` (Task 3 entry in "Tasks done", `gmail` package and new tables in Repository, `FakeGoogleApi` in Backend commands, `app.workers.enabled=false` in tests)

- [ ] **Step 1: Write `docs/task-3-gmail.md`** in the style of `task-2-documents.md`: big picture diagram (connect → scan → review → import → read), files table (backend + frontend), tables, endpoints, how OAuth/state/PKCE/encryption work, the workers and why the DB is the queue, the rules, Google Cloud setup (reuse the existing client; redirect URI; **add every Gmail address as a Test user**; Testing tokens end after 7 days), try-it steps, tests table, common problems (from spec + dev's table), known limits (no body-only bills, no daily scan).
- [ ] **Step 2: Update the other three files.**
- [ ] **Step 3: Final verification.** Backend: `./mvnw test -Dmaven.compiler.useIncrementalCompilation=false` → `BUILD SUCCESS`. Frontend: `npm run typecheck && npm run build` → success.
- [ ] **Step 4: Checkpoint** — report to the user; they commit.
