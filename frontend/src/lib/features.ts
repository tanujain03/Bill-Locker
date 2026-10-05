import { config } from './config';

/**
 * Parts of the app that need their own backend endpoints. The Spring Boot backend
 * is built step by step, so against the real API the UI only shows what it
 * already serves; with the mock API (VITE_API_MOCKING=true) everything is on.
 *
 * Always available: register/login, settings, and documents (upload, list,
 * preview, download, delete).
 */
export type Feature =
  | 'dashboard'
  | 'products'
  | 'warranties'
  | 'services'
  | 'assistant'
  /** Natural-language search in the top bar. */
  | 'search'
  | 'notifications'
  | 'gmail'
  /** OCR + AI extraction after upload (UPLOADED → PROCESSING → REVIEW_REQUIRED). */
  | 'documentProcessing';

/** Features the real backend already implements. Add one here when its endpoints exist. */
const BACKEND_FEATURES: ReadonlySet<Feature> = new Set<Feature>([
  'documentProcessing', // step 4: uploads are read (PDF text / OCR) and their details found
  'products', // step 5: products, categories, and "Confirm & Save" of a read document
  'warranties', // step 5: warranty status and the warranties page
  'dashboard', // step 5: the dashboard summary
  'services', // step 6: service records and next-service dates
  'notifications', // step 6: the bell, daily reminders and "document ready" notices
  'search', // step 6: plain-English search (rules for now, AI later)
  'gmail', // step 7: import bills from Gmail
]);

export function isFeatureEnabled(feature: Feature): boolean {
  return config.apiMocking || BACKEND_FEATURES.has(feature);
}

/** Where signed-in users land: the dashboard once it exists, the documents page until then. */
export function homePath(): string {
  return isFeatureEnabled('dashboard') ? '/dashboard' : '/documents';
}
