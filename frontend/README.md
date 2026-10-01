# Bill Locker — Frontend

React + TypeScript web app for **Bill Locker — Never Lose a Bill. Never Miss a Warranty.**

It covers the whole product flow: sign-up, bill upload with live AI-processing
progress, the AI extraction review (confidence per field, nothing saved until you
confirm), products and warranties, service tracking, notifications, the AI
assistant, natural-language search and **Gmail import**.

Until the Spring Boot backend exists, the app runs against an **in-browser mock
API** (Mock Service Worker) that implements the full REST contract with realistic
demo data. Switching to the real backend is one environment variable.

## Quick start

Requires Node.js ≥ 20.18.

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and click **“Explore with the demo account”**
(`demo@billlocker.app` / `Demo@1234`), or register a new account to start with an
empty locker.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server on :5173 (mock API on by default) |
| `npm run build` | Type-check and build to `dist/` |
| `npm run preview` | Serve the production build on :4173 |
| `npm run typecheck` | TypeScript only |
| `npm run lint` | ESLint |
| `npm test` | Vitest (unit + integration tests against the mock API) |

## Configuration

Copy `.env.example` → `.env.local` (or `.env.development.local`) to override the
defaults in `.env.development`. All `VITE_*` values are public — never put secrets here.

| Variable | Default (dev) | Meaning |
|---|---|---|
| `VITE_API_MOCKING` | `true` | `true` = in-browser mock API; `false` = real backend |
| `VITE_API_BASE_URL` | `/api` | API base path (proxied to the backend in dev and Docker) |
| `VITE_PROXY_TARGET` | `http://localhost:8080` | Dev-server proxy target for `/api` |
| `VITE_SHOW_DEMO_LOGIN` | `true` | Show the demo-account button on the login page |
| `VITE_MAX_UPLOAD_MB` | `10` | Client-side upload limit (the server enforces its own) |

**Use the real backend:** start it on :8080, then run with
`VITE_API_MOCKING=false` (e.g. in `.env.development.local`). The contract it must
implement is in [`../docs/api-contract.md`](../docs/api-contract.md).

## The mock API (demo mode)

`src/mocks/` implements every endpoint of the contract, with per-user data
isolation, validation and the same error format as the backend. Data is kept in
`localStorage` (uploaded files only until reload).

- **Demo data**: 9 products (Dell laptop, Samsung refrigerator, Sony TV, LG washing
  machine, Voltas AC, Apple iPhone, boAt headphones, HP printer, a sofa) with
  invoices, service history and reminders; one bill waiting for review.
  All sellers, invoice and serial numbers are fictional.
- **Document pipeline**: uploads go `UPLOADED → OCR → AI extraction → indexing →
  REVIEW_REQUIRED` over ~6 s. The simulated extraction is picked from the file name
  (`dell…`, `iphone…`, `samsung…`, `sony…`, `lg…`, `voltas…`, `boat…`, `hp…`), otherwise
  a partially-readable bill with some fields “Not found”. A file name containing
  `fail`, `blurry` or `corrupt` fails once (to demo retry).
- **Gmail**: “Connect Gmail” simulates the OAuth consent; the first scan finds 6
  emails (invoices, a service receipt, a warranty certificate and a food order the
  AI rates as unlikely to be a bill).
- **AI assistant & search**: answered strictly from the user’s own data — the
  mock says so when something isn’t on the documents.
- **Reset**: Settings → “Reset demo data”, or `__billLocker.resetMockData()` in the console.

## Project structure

```
src/
├── components/     UI kit (ui/) and feature components (documents/, products/,
│                   warranties/, services/, notifications/, assistant/, gmail/, charts/, auth/)
├── pages/          One file per route (lazy-loaded when signed in)
├── layouts/        App shell (sidebar, top bar, mobile drawer) and auth layout
├── services/       Axios calls, one module per API area
├── hooks/          TanStack Query hooks and small UI hooks
├── lib/            API client + error normalisation, auth context, query keys, config
├── types/          API contract types (mirrors docs/api-contract.md)
├── utils/          Dates (warranty maths preview), formatting, labels, search helpers
├── mocks/          Mock API: handlers, seed data, simulated OCR/AI, PDF previews
└── test/           Test setup and integration tests
```

## Routes

| Route | Page |
|---|---|
| `/` | Landing page |
| `/login`, `/register` | Auth (redirect to where you were heading) |
| `/dashboard` | Stats, spending by category, warranty status, upcoming expirations/services, recent documents |
| `/products`, `/products/:id` | Product grid with filters + AI search; product details, warranty, documents, service history |
| `/documents`, `/documents/:id` | Upload, status filters; processing progress, **AI extraction review**, preview |
| `/warranties` | All warranties by status, soonest expiry first |
| `/services` | Upcoming services and service history |
| `/assistant` | AI chat with suggested prompts and source references |
| `/gmail` | Connect Gmail, scan, import/ignore detected bills |
| `/notifications` | Notification center |
| `/profile` | Settings: profile, connected accounts, privacy, sign out |

## Design & accessibility

- Tailwind CSS v4 with design tokens in `src/index.css` (`brand-*` indigo scale, slate neutrals).
- Status colours are reserved for warranty/processing state and always come with an
  icon and a label. Chart colours were checked with a colour-blind-safety validator;
  every chart has a text/table equivalent.
- Keyboard-accessible dialogs (focus trap, Escape, focus return), skip link, labelled
  form fields with inline errors, `prefers-reduced-motion` respected.
- Mobile first where it matters: camera capture for bills, bottom-sheet dialogs,
  floating “Add bill” button.

## Security notes

- The JWT is stored via `src/lib/token-storage.ts` (localStorage for the hackathon;
  one module to change for httpOnly cookies). Any 401 clears the session.
- The UI never renders HTML from the API (`RichText` supports plain paragraphs,
  lists and bold only). User-facing error messages come from the API’s `message`.
- Uploads are pre-validated (type, extension, size) — the backend re-validates.
- The Gmail OAuth redirect is only followed to `accounts.google.com` or this origin.
- The Docker image serves the app with a strict Content-Security-Policy.

## Docker

```bash
docker build -t bill-locker-frontend ./frontend
docker run -p 3000:80 -e BACKEND_URL=http://host.docker.internal:8080 bill-locker-frontend
```

nginx serves the SPA and proxies `/api` to `BACKEND_URL` (default `http://backend:8080`,
the compose service name). Build a self-contained demo image with the mock API:
`docker build --build-arg VITE_API_MOCKING=true -t bill-locker-demo ./frontend`.
