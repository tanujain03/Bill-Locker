# Task 6 — Bill assistant chatbot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A floating "Ask to your locker" chat that answers questions about the signed-in user's own data by having Gemini write a `SELECT`, which the backend runs read-only over user-filtered views.

**Architecture:** New backend package `project.bill_locker.assistant`. At startup `AssistantDatabaseSetup` creates six `ai_*` views (filtered by the `app.user_id` setting) and a `bill_locker_ai` role that can only read them. `ReadOnlyQueryRunner` runs Gemini's SQL in a read-only transaction as that role. `AssistantService` calls Gemini twice: plan (SQL or direct reply), then answer from rows. Frontend `AssistantChat` lives in `AppLayout`.

**Tech Stack:** Spring Boot 4.1.1 / Java 21 / `JdbcTemplate` + `TransactionTemplate` / `RestClient` + Jackson 3 (`tools.jackson`) / PostgreSQL 17; React 19 + TypeScript 5.9 + Tailwind 4 + lucide-react 1.x.

**Spec:** `docs/superpowers/specs/2026-10-09-task-6-assistant-design.md`

## Global Constraints

- No new libraries (backend or frontend). No Flyway / migration files: views and role are created from Java strings at startup.
- Never commit, merge or push. The "commit" step of each task is: stop and tell the user which files changed.
- Gemini is never called in tests (`FakeAssistantModel`; `MockRestServiceServer` for `GeminiAssistantModel`).
- User id only from the JWT (`CurrentUser.id(jwt)`); Gemini never sees or chooses it.
- Guards: SQL starts with `select`/`with`, no `;` (one trailing `;` stripped), no `set_config`/`pg_settings`; read-only transaction; `statement_timeout = '3s'`; role `bill_locker_ai`; max 50 rows.
- Errors: no key → 503 `AI_NOT_CONFIGURED` "The assistant isn't set up (no GEMINI_API_KEY)."; Gemini fails → 503 `ASSISTANT_FAILED` with Gemini's friendly message; SQL fails twice → 200 answer "I couldn't look that up. Try asking in a different way."
- Request: 1–10 messages, `role` ∈ {`user`,`assistant`}, `text` 1–2000 chars, last message from `user`.
- Panel header text: "Ask to your locker". Answers are plain text, never HTML; `/documents/<uuid>` becomes a `<Link>`.
- While IntelliJ runs the app, build with `-Dmaven.compiler.useIncrementalCompilation=false`.

## Review Focus

1. A slow query (`SELECT pg_sleep(10)`) must fail after ~3 s, not hang the request → Task 1 test `slowQueryIsCancelled`.
2. AI SQL that switches the user (`set_config('app.user_id', …)` inside a `WITH`) must never show another user's rows → Task 1 tests `setConfigTrickIsRejected`, `setConfigIsRevokedForRole`.
3. Pooled connections must not keep the AI role or user setting after a run → Task 1 test `connectionIsCleanAfterRun`.
4. Large answers are capped at 50 rows → Task 1 test `atMostFiftyRows`.
5. A plain `SELECT` with a trailing `;` (very common from LLMs) must still run → Task 1 test `trailingSemicolonIsAllowed`.

---

### Task 1: Read-only data access (views, role, query runner)

**Files:**
- Create: `backend/src/main/java/project/bill_locker/assistant/AssistantDatabaseSetup.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/ReadOnlyQueryRunner.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/QueryRejectedException.java`
- Test: `backend/src/test/java/project/bill_locker/assistant/ReadOnlyQueryRunnerTests.java`

**Interfaces:**
- Produces: `public class ReadOnlyQueryRunner { public List<Map<String, Object>> run(UUID userId, String sql) throws QueryRejectedException }` — Spring `@Component`. Row maps are `LinkedHashMap` in column order; values: `null`, `Number` and `Boolean` kept, everything else `toString()` (UUID, dates, timestamps).
- Produces: `public class QueryRejectedException extends RuntimeException { QueryRejectedException(String message) }` — message is the reason (our own text, or Postgres's most specific cause message).
- Produces: `AssistantDatabaseSetup.VIEWS_DESCRIPTION` (`public static final String`): a plain-text list of the six views and their columns, used by the Gemini prompt in Task 2. Keep it next to the view SQL so they change together.

- [ ] **Step 1: Write the failing tests** in `ReadOnlyQueryRunnerTests extends DocumentApiTestBase`. `@Autowired ReadOnlyQueryRunner runner; @Autowired JdbcTemplate jdbc;`. Setup per test: register users A and B (`registerAndGetToken`), each saves a bill with `saveBill(token, …)` (A: seller "Croma", total 1499; B: seller "Reliance", total 999). Get user ids with `jdbc.queryForObject("select id from users where email = ?", UUID.class, email)`.

```java
@Test void onlyOwnRows() {
    var rows = runner.run(userA, "SELECT seller_name FROM ai_documents");
    assertThat(rows).extracting(r -> r.get("seller_name")).containsExactly("Croma");
}
@Test void noUserSettingMeansNoRows() {   // views return nothing when app.user_id is unset
    assertThat(jdbc.queryForList("SELECT * FROM ai_documents")).isEmpty();
}
@Test void deleteIsRejectedByTextCheck() {
    assertThatThrownBy(() -> runner.run(userA, "DELETE FROM documents")).isInstanceOf(QueryRejectedException.class);
    assertThat(runner.run(userA, "SELECT id FROM ai_documents")).hasSize(1);
}
@Test void writeInsideWithIsRejectedByDatabase() {
    assertThatThrownBy(() -> runner.run(userA, "WITH x AS (DELETE FROM documents RETURNING id) SELECT * FROM x"))
        .isInstanceOf(QueryRejectedException.class);
    assertThat(runner.run(userA, "SELECT id FROM ai_documents")).hasSize(1);
}
@Test void realTablesAreOffLimits() {
    assertThatThrownBy(() -> runner.run(userA, "SELECT password_hash FROM users")).isInstanceOf(QueryRejectedException.class);
    assertThatThrownBy(() -> runner.run(userA, "SELECT * FROM documents")).isInstanceOf(QueryRejectedException.class);
}
@Test void twoStatementsAreRejected() {
    assertThatThrownBy(() -> runner.run(userA, "SELECT 1; DROP TABLE documents")).isInstanceOf(QueryRejectedException.class);
}
@Test void trailingSemicolonIsAllowed() {
    assertThat(runner.run(userA, "SELECT count(*) AS n FROM ai_documents;  ")).containsExactly(Map.of("n", 1L));
}
@Test void setConfigTrickIsRejected() {
    String sql = "WITH x AS (SELECT set_config('app.user_id', '" + userB + "', true)) SELECT seller_name FROM x, ai_documents";
    assertThatThrownBy(() -> runner.run(userA, sql)).isInstanceOf(QueryRejectedException.class);
}
@Test void setConfigIsRevokedForRole() {   // bypasses the text check: the database itself must refuse
    assertThat(jdbc.queryForObject(
        "SELECT has_function_privilege('bill_locker_ai', 'pg_catalog.set_config(text,text,boolean)', 'EXECUTE')",
        Boolean.class)).isFalse();
}
@Test void slowQueryIsCancelled() {
    long start = System.nanoTime();
    assertThatThrownBy(() -> runner.run(userA, "SELECT pg_sleep(10)")).isInstanceOf(QueryRejectedException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(6));
}
@Test void atMostFiftyRows() {
    assertThat(runner.run(userA, "SELECT g FROM generate_series(1, 100) g")).hasSize(50);
}
@Test void connectionIsCleanAfterRun() {
    for (int i = 0; i < 5; i++) runner.run(userA, "SELECT 1 AS one");
    assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isNotEqualTo("bill_locker_ai");
    assertThat(jdbc.queryForObject("SELECT coalesce(current_setting('app.user_id', true), '')", String.class)).isEmpty();
}
@Test void itemsAndGmailViewsAreFiltered() {
    // every ai_* view is queryable and returns only A's rows (B has none of A's items)
    for (String v : List.of("ai_me", "ai_documents", "ai_items", "ai_gmail_accounts", "ai_gmail_emails", "ai_gmail_files"))
        assertThatCode(() -> runner.run(userA, "SELECT * FROM " + v)).doesNotThrowAnyException();
    assertThat(runner.run(userA, "SELECT email FROM ai_me")).extracting(r -> r.get("email")).containsExactly(emailA);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run (in `backend/`): `./mvnw test -Dtest=ReadOnlyQueryRunnerTests`
Expected: compile failure — `ReadOnlyQueryRunner` not found.

- [ ] **Step 3: Implement `AssistantDatabaseSetup`** — `@Component`, `@EventListener(ApplicationReadyEvent.class) void setUp()` using `JdbcTemplate`. Statements, in order:
  1. `DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bill_locker_ai') THEN CREATE ROLE bill_locker_ai NOLOGIN; END IF; END $$`
  2. `GRANT bill_locker_ai TO CURRENT_USER` (needed for `SET ROLE` when the app user isn't a superuser; harmless otherwise).
  3. `REVOKE EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) FROM PUBLIC`, `GRANT EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) TO CURRENT_USER`, `REVOKE UPDATE ON pg_catalog.pg_settings FROM PUBLIC`.
  4. For each view: `DROP VIEW IF EXISTS <v>` then `CREATE VIEW <v> AS …` (drop+create, so column changes never fail), then `GRANT SELECT ON <v> TO bill_locker_ai`.
  Filter for every view: `… = current_setting('app.user_id', true)::uuid`. Columns per spec §3.1 (`ai_documents`: every `documents` column except `user_id`; `ai_items`: `document_items` joined on `documents.user_id`; `ai_gmail_files`: joined on `gmail_emails.user_id`). Wrap all in try/catch: on failure `log.warn("Assistant database setup failed, the assistant won't work: …")`, never stop the app.

- [ ] **Step 4: Implement `ReadOnlyQueryRunner.run`** — text checks per Global Constraints (lower-cased for `set_config`/`pg_settings`), then one `TransactionTemplate` callback that always calls `status.setRollbackOnly()`. Statement order, first one first: `SET TRANSACTION READ ONLY`, `SET LOCAL statement_timeout = '3s'`, `SELECT set_config('app.user_id', ?, true)`, `SET LOCAL ROLE bill_locker_ai`, then the query via a `JdbcTemplate` whose `maxRows` is 50. Catch `DataAccessException` → `QueryRejectedException(e.getMostSpecificCause().getMessage())`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw test -Dtest=ReadOnlyQueryRunnerTests`
Expected: all tests PASS.

- [ ] **Step 6: Stop and report** changed files to the user (no commit).

---

### Task 2: Gemini model (plan + answer)

**Files:**
- Create: `backend/src/main/java/project/bill_locker/assistant/ChatMessage.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/Plan.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/AssistantModel.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/AssistantException.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/GeminiAssistantModel.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/AssistantModelConfig.java`
- Test: `backend/src/test/java/project/bill_locker/assistant/GeminiAssistantModelTests.java`

**Interfaces:**
- Consumes: `AssistantDatabaseSetup.VIEWS_DESCRIPTION` (Task 1).
- Produces:
  - `public record ChatMessage(@NotBlank @Pattern(regexp = "user|assistant") String role, @NotBlank @Size(max = 2000) String text)`
  - `public record Plan(Action action, String sql, String reply) { public enum Action { QUERY, REPLY } }`
  - `public interface AssistantModel { Plan plan(List<ChatMessage> messages, LocalDate today, String failedSql, String error); String answer(List<ChatMessage> messages, String sql, List<Map<String, Object>> rows); }` — `failedSql`/`error` are `null` on the first try.
  - `public class AssistantException extends RuntimeException { public static final String NOT_CONFIGURED = "AI_NOT_CONFIGURED"; public static final String FAILED = "ASSISTANT_FAILED"; AssistantException(String code, String message); String getCode(); }`

- [ ] **Step 1: Write the failing tests** — copy the setup style of `document/ai/GeminiDetailExtractorTests` (`MockRestServiceServer.bindTo(RestClient.builder())`, models `List.of("m1")` or `List.of("m1", "m2")`).

```java
@Test void planReadsQueryAnswer() {
    gemini.expect(requestTo(containsString("/models/m1:generateContent")))
          .andExpect(content().string(containsString("ai_documents")))       // view list is in the prompt
          .andExpect(content().string(containsString("2026-10-07")))         // today is in the prompt
          .andRespond(withSuccess(geminiReply("{\"action\":\"QUERY\",\"sql\":\"SELECT 1\",\"reply\":null}"), APPLICATION_JSON));
    assertThat(model.plan(List.of(new ChatMessage("user", "hi")), LocalDate.of(2026, 10, 7), null, null))
        .isEqualTo(new Plan(Plan.Action.QUERY, "SELECT 1", null));
}
@Test void retrySendsTheErrorBack() { /* expect content containing "column \"x\" does not exist" when plan(..., "SELECT x", "column \"x\" does not exist") */ }
@Test void answerReturnsTextAndSendsRows() { /* expect content containing "Croma"; reply text "You spent ₹1,499." → answer(...) equals it */ }
@Test void busyModelFallsBackToTheNextOne() { /* m1 → 503, m2 → success */ }
@Test void quotaUsedUpGivesFriendlyMessage() { /* 429 on only model → AssistantException code ASSISTANT_FAILED, message contains "free Gemini limit" */ }
@Test void badKeyGivesCheckKeyMessage() { /* 401 → ASSISTANT_FAILED, message contains "GEMINI_API_KEY" */ }
```
(`geminiReply(text)` builds `{"candidates":[{"content":{"parts":[{"text": <text>}]}}]}`.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=GeminiAssistantModelTests`
Expected: compile failure — `GeminiAssistantModel` not found.

- [ ] **Step 3: Implement the records, interface, exception, and `GeminiAssistantModel(RestClient restClient, String apiKey, List<String> models)`.** Same request shape and model-fallback loop as `GeminiDetailExtractor` (503/429 → next model; other status or no connection → `AssistantException(FAILED, messageFor(status))`, same friendly texts reworded for the assistant). Conversation goes in `contents` as alternating `user`/`model` roles. `plan` uses `generationConfig.responseMimeType = application/json` + `responseSchema` `{action: enum[QUERY,REPLY], sql: string nullable, reply: string nullable}`; `answer` uses plain text. Prompt text per spec §4.3 (plan prompt includes `VIEWS_DESCRIPTION` and today's date; answer prompt includes the SQL and the rows as JSON).

- [ ] **Step 4: Implement `AssistantModelConfig`** — `@Configuration`, a `@Bean AssistantModel` built exactly like `DetailExtractorConfig` (same `app.gemini.*` properties; blank key → an anonymous `AssistantModel` class whose both methods throw `AssistantException(NOT_CONFIGURED, "The assistant isn't set up (no GEMINI_API_KEY).")`).

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw test -Dtest=GeminiAssistantModelTests`
Expected: all PASS.

- [ ] **Step 6: Stop and report** changed files (no commit).

---

### Task 3: Chat endpoint (service + controller)

**Files:**
- Create: `backend/src/main/java/project/bill_locker/assistant/ChatRequest.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/ChatResponse.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/AssistantService.java`
- Create: `backend/src/main/java/project/bill_locker/assistant/AssistantController.java`
- Create: `backend/src/test/java/project/bill_locker/FakeAssistantModel.java`
- Modify: `backend/src/test/java/project/bill_locker/ApiTest.java` (add `FakeAssistantModel.Config.class` to `@Import`)
- Test: `backend/src/test/java/project/bill_locker/assistant/AssistantApiTests.java`

**Interfaces:**
- Consumes: `ReadOnlyQueryRunner.run` (Task 1); `AssistantModel`, `Plan`, `ChatMessage`, `AssistantException` (Task 2); `Clock` bean.
- Produces: `POST /api/assistant/chat`; `record ChatRequest(@NotEmpty @Size(max = 10) List<@Valid ChatMessage> messages)`; `record ChatResponse(boolean success, String answer, String sql)`.
- Produces (tests): `FakeAssistantModel implements AssistantModel` — `queuePlan(Plan)` (plans returned in order; default `REPLY "Hello"`), `answer` returns `"rows=" + rows`, `planCalls()` returns recorded `(failedSql, error)` pairs, `reset()`. `@Primary` bean in nested `Config`, like `FakeRegistrationFinder`.

- [ ] **Step 1: Write the failing tests** in `AssistantApiTests extends DocumentApiTestBase` (`@BeforeEach fake.reset()`):

```java
@Test void queryPlanRunsSqlAndAnswersFromOwnRows() {
    // A saves "Croma" bill, B saves "Reliance" bill
    fake.queuePlan(new Plan(QUERY, "SELECT seller_name FROM ai_documents", null));
    chat(tokenA, "[{\"role\":\"user\",\"text\":\"my shops?\"}]")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("rows=[{seller_name=Croma}]"))
        .andExpect(jsonPath("$.sql").value("SELECT seller_name FROM ai_documents"));
}
@Test void replyPlanRunsNoQuery() { /* queue REPLY "I can only look things up…" → answer equals it, $.sql is null */ }
@Test void badSqlIsRetriedOnceWithTheError() {
    fake.queuePlan(new Plan(QUERY, "SELECT nope FROM ai_documents", null));
    fake.queuePlan(new Plan(QUERY, "SELECT count(*) AS n FROM ai_documents", null));
    // → 200, answer "rows=[{n=1}]"; planCalls().get(1) has failedSql "SELECT nope FROM ai_documents" and error containing "nope"
}
@Test void failingTwiceGivesFriendlyAnswer() { /* two bad plans → 200, answer "I couldn't look that up. Try asking in a different way." */ }
@Test void deleteSqlFromModelChangesNothing() { /* plan "DELETE FROM documents" twice → 200 friendly answer; GET /api/documents still lists the bill */ }
@Test void notConfiguredIs503() { /* fake.throwOnPlan(new AssistantException(NOT_CONFIGURED, …)) → 503, $.code AI_NOT_CONFIGURED */ }
@Test void validation() {
    chat(tokenA, "[]").andExpect(status().isBadRequest());
    chat(tokenA, elevenMessages).andExpect(status().isBadRequest());
    chat(tokenA, "[{\"role\":\"assistant\",\"text\":\"hi\"}]").andExpect(status().isBadRequest());
    chat(tokenA, "[{\"role\":\"user\",\"text\":\"" + "x".repeat(2001) + "\"}]").andExpect(status().isBadRequest());
}
@Test void needsToken() { /* no Authorization header → 401 */ }
```
(`chat(token, messagesJson)` posts `{"messages": <json>}` with the bearer token. `FakeAssistantModel` therefore also needs `throwOnPlan(RuntimeException)`.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=AssistantApiTests`
Expected: compile failure — `ChatRequest`/`FakeAssistantModel` not found.

- [ ] **Step 3: Implement `AssistantService.chat(UUID userId, List<ChatMessage> messages): ChatResponse`** — last message must be `user`, else `ApiException(400, "VALIDATION_FAILED", …, Map.of("messages", "The last message must be from the user."))`. Flow per spec §4.2 (at most 2 plan calls). `AssistantException` → `ApiException(503, e.getCode(), e.getMessage())`. No `@Transactional`. `today = LocalDate.now(clock)`.

- [ ] **Step 4: Implement `AssistantController`** — `@RestController @RequestMapping("/api/assistant")`, `@PostMapping("/chat") ChatResponse chat(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest request)`.

- [ ] **Step 5: Run tests to verify they pass, then the whole suite**

Run: `./mvnw test`
Expected: all tests PASS (old ones too).

- [ ] **Step 6: Stop and report** changed files (no commit).

---

### Task 4: Floating chat panel + docs

**Files:**
- Modify: `frontend/src/lib/api.ts` (types + `askAssistant`)
- Create: `frontend/src/components/assistant/AssistantChat.tsx`
- Modify: `frontend/src/components/AppLayout.tsx` (render `<AssistantChat />` next to `<DropAnywhere />`)
- Create: `docs/task-6-assistant.md`
- Modify: `docs/api-contract.md`, `CLAUDE.md`

**Interfaces:**
- Consumes: `POST /api/assistant/chat` (Task 3).
- Produces: `export type ChatMessage = { role: 'user' | 'assistant'; text: string }`; `export type ChatAnswer = { answer: string; sql: string | null }`; `export function askAssistant(messages: ChatMessage[]): Promise<ChatAnswer>` (sends the last 10 via `api()`).

- [ ] **Step 1: Add the types and `askAssistant`** to `lib/api.ts`.

- [ ] **Step 2: Build `AssistantChat`** per spec §5:
  - Button `fixed right-4 bottom-20 sm:bottom-6 z-40`, `MessageCircle` icon, `aria-label="Open assistant"`, toggles the panel.
  - Panel: `sm:w-[380px] sm:h-[560px]`, full screen below `sm`. Header "Ask to your locker", "New chat" (clears messages), close ✕.
  - Message list `aria-live="polite"`; "Looking it up…" bubble while waiting; three example questions when empty ("How much did I spend this month?", "Which warranties expire soon?", "Show my watch bill").
  - Textarea: Enter sends, Shift+Enter new line. Send button via `<Button variant="primary">`.
  - "Show query" toggle per answer with `sql`, shown in a `<pre>`.
  - Answer text: split on `/\/documents\/[0-9a-f-]{36}/g`; matches become `<Link to=…>` (closes the panel below `sm`), the rest is plain text with `whitespace-pre-wrap`.
  - Errors become an assistant bubble with `errorMessage(e)`.
  - Esc closes the panel and focuses the button. Message state lives in this component (mounted once in `AppLayout`).

- [ ] **Step 3: Verify the types and build**

Run (in `frontend/`): `npm run typecheck` then `npm run build`
Expected: no errors.

- [ ] **Step 4: Check in the browser** (backend + `npm run dev`). Sign in, open the chat, ask "How much did I spend this month?" and "delete my watch bill", open a `/documents/<id>` link, toggle "Show query". Check at 375 px that the button sits above the tab bar and the panel fills the screen. Take a screenshot.

- [ ] **Step 5: Write docs.**
  - `docs/task-6-assistant.md`: what happens where, style of `docs/task-5-warranty-registration.md`. Cover the guards and why each exists, including the `set_config` trick.
  - `docs/api-contract.md`: add `POST /api/assistant/chat` and list it as built.
  - `CLAUDE.md`: add a "Task 6" bullet under "Tasks done on `restart`".

- [ ] **Step 6: Stop and report** changed files and the screenshot (no commit).
