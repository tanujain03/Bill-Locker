# Task 7 — Going live (Neon + Render + Vercel)

Bill Locker runs as three parts, each on a host that suits it:

```
Browser
  https://<your-app>.vercel.app              Vercel: the React build (static files)
    /api/...  ── Vercel rewrite ──►  https://bill-locker-backend.onrender.com/api/...
                                       Render: Spring Boot in a Docker container
                                         └──► Neon: PostgreSQL (branch "production")
```

- **Vercel** only serves files, it can't run Java. Its rewrite forwards every `/api/...` call to
  Render, so the browser only ever talks to the Vercel address: no CORS, and the `gmail_connect`
  cookie keeps working (same as Vite's proxy on your PC).
- **Render** runs the backend all the time (the workers poll every 3 s).
- **Neon** holds the data, including the uploaded files (`document_files`). No bucket needed.

**Local stays local:** on your PC `backend/.env` points at your own PostgreSQL. Live, the
same code reads Render's environment variables and connects to Neon. The code never contains
an address or a password, only `${DATABASE_URL}` placeholders.

New files: `backend/Dockerfile`, `backend/.dockerignore`, `frontend/vercel.json`.
Changed: `application.properties` (`server.port`, `server.forward-headers-strategy`).

---

## 1. What changed in the code

| File | What and why |
|---|---|
| `backend/Dockerfile` | Stage 1 (Maven image) builds the jar with `mvn package -DskipTests` (tests need Docker, which Render's build doesn't have; run them on your PC). Stage 2 (Java-only image) runs `java -jar app.jar`. `MaxRAMPercentage=75` lets Java use most of the small container. |
| `backend/.dockerignore` | Keeps `.env` (secrets) and `target/` out of the image. |
| `application.properties` | `server.port=${PORT:8080}`: Render tells the app its port via `PORT`. `server.forward-headers-strategy=framework`: behind HTTPS proxies the app trusts `X-Forwarded-Proto`, so `request.isSecure()` is true and the Gmail cookie is `Secure`. |
| `frontend/vercel.json` | Rule 1: `/api/*` → the Render backend. Rule 2: everything else → `index.html`, so refreshing `/documents/123` loads React instead of a 404. |

---

## 2. Neon (done)

Project `Bill-Locker`, branch `production`, region AWS us-east-2 (Ohio). Only "Postgres database"
is on. Use the **direct** host (without `-pooler`): Spring already pools connections (HikariCP).
Tables are created by Hibernate on the backend's first start.

```
DATABASE_URL=jdbc:postgresql://ep-billowing-mountain-b475mnil.c-6.us-east-2.aws.neon.tech/neondb?sslmode=require
DATABASE_USERNAME=neondb_owner
DATABASE_PASSWORD=<from Neon>
```

---

## 3. Render (backend)

1. Push the branch to GitHub.
2. render.com → **New → Web Service** → connect the GitHub repo.
3. Settings:
   - **Name:** `bill-locker-backend` (this becomes `bill-locker-backend.onrender.com`; if you pick
     another name, change the address in `frontend/vercel.json`).
   - **Branch:** the one you deploy (e.g. `restart` or `main`).
   - **Region:** **Ohio (US East)**, next to Neon.
   - **Root Directory:** `backend`
   - **Language / Runtime:** **Docker** (Render finds `backend/Dockerfile`).
   - **Instance type:** Free to try (sleeps after 15 min without visits, first visit then takes
     ~1 min; workers stop while asleep). Starter (paid) for always-on.
   - **Health Check Path:** leave empty.
4. **Environment** (Add environment variable), one per line:

   | Key | Value |
   |---|---|
   | `DATABASE_URL` | the Neon JDBC URL above |
   | `DATABASE_USERNAME` | `neondb_owner` |
   | `DATABASE_PASSWORD` | Neon password |
   | `JWT_SECRET` | a **new** random 64-character string (not your local one) |
   | `FRONTEND_URL` | `https://<your-app>.vercel.app` (fill in after step 4) |
   | `GEMINI_API_KEY` | your key (optional `GEMINI_MODEL`) |
   | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | same as local (Gmail App Password) |
   | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | same as local |
   | `GMAIL_TOKEN_KEY` | a **new** key (`openssl rand -base64 32`) |
   | `GMAIL_REDIRECT_URI` | `https://<your-app>.vercel.app/api/integrations/gmail/callback` |

   Don't set `PORT`: Render sets it.
5. **Create Web Service.** Watch **Logs**: the build takes a few minutes, then
   `Started BillLockerApplication`. Check `https://bill-locker-backend.onrender.com/api/users/me`
   in the browser: a JSON 401 means the backend is up.

---

## 4. Vercel (frontend)

1. vercel.com → **Add New → Project** → import the GitHub repo.
2. **Root Directory:** `frontend`. Framework: **Vite** (build `npm run build`, output `dist`).
3. Settings → General → **Node.js Version:** 20.x.
4. No environment variables: the frontend only calls `/api/...`.
5. **Deploy.** Copy the address (e.g. `https://bill-locker.vercel.app`), then go back to Render
   and set `FRONTEND_URL` and `GMAIL_REDIRECT_URI` with it (Render redeploys by itself).

---

## 5. Google Cloud Console (Gmail import)

APIs & Services → Credentials → your OAuth client → **Authorized redirect URIs** → add
`https://<your-app>.vercel.app/api/integrations/gmail/callback` (keep the localhost one for your
PC). While the consent screen is in "Testing", only the test users you list can connect Gmail.

---

## 6. Check it works

Sign up (Neon starts empty) → upload a bill → AI read → save → dashboard → forgot password
(email link opens the Vercel address) → connect Gmail → ask the chat assistant.

**Chat assistant on Neon:** at startup `AssistantDatabaseSetup` revokes `set_config` and the
advisory-lock functions from PUBLIC. Neon's `neondb_owner` is not a superuser, so these may fail
(a warning in Render's logs). Then `ReadOnlyQueryRunner` refuses on purpose (503) and only the
chat is off; everything else works.

---

## 7. Every day after this

- Develop on your PC as before (local DB, `npm run dev`, IntelliJ **Backend**).
- `git push` to the deployed branch → Render and Vercel rebuild by themselves.
- Secrets only in `backend/.env` (PC) and Render's Environment (live). Never in git.
