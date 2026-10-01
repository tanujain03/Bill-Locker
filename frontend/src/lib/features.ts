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
const BACKEND_FEATURES: ReadonlySet<Feature> = new Set<Feature>([]);

export function isFeatureEnabled(feature: Feature): boolean {
  return config.apiMocking || BACKEND_FEATURES.has(feature);
}

/** Where signed-in users land: the dashboard once it exists, the documents page until then. */
export function homePath(): string {
  return isFeatureEnabled('dashboard') ? '/dashboard' : '/documents';
}
