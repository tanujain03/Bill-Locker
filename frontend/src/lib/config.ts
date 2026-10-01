const env = import.meta.env;

const maxUploadMb = Number(env.VITE_MAX_UPLOAD_MB) || 10;

export const config = {
  appName: 'Bill Locker',
  tagline: 'Never Lose a Bill. Never Miss a Warranty.',
  apiBaseUrl: env.VITE_API_BASE_URL?.trim() || '/api',
  /** When true the app talks to the in-browser mock API (see src/mocks). */
  apiMocking: env.VITE_API_MOCKING === 'true',
  showDemoLogin: env.VITE_SHOW_DEMO_LOGIN === 'true',
  maxUploadMb,
  maxUploadBytes: maxUploadMb * 1024 * 1024,
  /** A warranty is "expiring soon" within this many days (mirrors the backend rule). */
  expiringSoonDays: 30,
} as const;

/** Fictional demo account seeded by the backend and the mock API. */
export const DEMO_CREDENTIALS = {
  email: 'demo@billlocker.app',
  password: 'Demo@1234',
} as const;
