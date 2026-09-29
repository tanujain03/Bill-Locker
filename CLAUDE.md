# CLAUDE.md

Guidance for AI-assisted work on Bill Locker (hackathon project; built in phases).

## Repository

- `frontend/` — React 19 + TypeScript + Vite 6 + Tailwind CSS 4 + TanStack Query 5 +
  React Router 7 + React Hook Form + Zod 3 + Recharts 3 + lucide-react 1.x. Complete.
- `docs/api-contract.md` — **source of truth** for the REST API. Frontend types in
  `frontend/src/types/` mirror it; the mock API in `frontend/src/mocks/` implements it.
- `bill-locker/` — Spring Initializr skeleton (Spring Boot 4.1.1, Java 21, package
  `project.bill_locker`). The spec wants `backend/` with package `com.billlocker`;
  decide/rename when starting the backend phase.

## Commands (run inside `frontend/`)

- `npm run dev` — dev server :5173, mock API on (`.env.development`)
- `npm test` — Vitest (jsdom + MSW); `npm run lint`; `npm run typecheck`; `npm run build`
- Node here is 20.18 — keep Vite 6 / Vitest 3 / TypeScript 5.9 (Vite 7+ needs Node ≥ 20.19).

## Conventions

- Changing an endpoint or payload? Update `docs/api-contract.md`, `src/types/`, the
  service in `src/services/`, and the mock handler in `src/mocks/handlers.ts` together.
- Data fetching only through hooks in `src/hooks/` (query keys in `src/lib/query-keys.ts`);
  writes call `invalidateLockerData()` so dashboard/lists stay consistent.
- Every API-backed view handles loading, error (with retry), empty and success states.
- Reuse `src/components/ui/*` (Button, Card, Field/Input/Select, Modal, Badge, FilterTabs,
  EmptyState/ErrorState, PageHeader, StatCard). Status colours are reserved for status
  and always paired with an icon + label.
- Never render API text as HTML (`RichText` is the only formatter). No `dangerouslySetInnerHTML`.
- AI results are suggestions: nothing becomes product/warranty data without the
  review screen's "Confirm & Save". Missing values are `null` → "Not found".
- Warranty expiry = start + months − 1 day, computed by code (`utils/date.ts` preview,
  backend authoritative). Expiring soon = ≤ 30 days.
- lucide-react 1.x renamed icons (e.g. `Trash` not `Trash2`, `CircleQuestionMark`,
  `ShieldQuestionMark`); the typechecker catches wrong names.
- Files exporting React components must export only components (react-refresh lint);
  put shared constants/helpers in sibling `.ts` files.

## Backend phase checklist (from the spec)

Spring Security + JWT, Flyway migrations V1–V9, PostgreSQL + pgvector, MinIO storage,
pluggable OCR (Document AI/Vision → Tesseract fallback), provider-agnostic AI
(`AIService`, `DocumentExtractionService`, `EmbeddingService`, `RagService`; Gemini
first), prompts in `ai/prompts/`, scheduled reminder job, Gmail OAuth (read-only scope,
encrypted refresh tokens, callback redirects to `/gmail?status=connected|error`),
Swagger, global exception handler returning `{ success:false, code, message, fieldErrors? }`,
tests (auth, authorization/isolation incl. RAG chunks, warranty maths, uploads, AI parsing),
Docker Compose (frontend, backend, postgres+pgvector, minio). Seed the demo account
`demo@billlocker.app` / `Demo@1234` with the same demo products as `src/mocks/seed.ts`.
