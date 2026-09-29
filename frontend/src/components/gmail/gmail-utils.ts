/**
 * The OAuth URL comes from our API; before sending the browser there we still
 * check it points to Google's consent screen or back into this app.
 */
export function isSafeAuthorizationUrl(url: string, origin: string = window.location.origin): boolean {
  try {
    const parsed = new URL(url, origin);
    if (parsed.origin === origin) return true;
    return parsed.protocol === 'https:' && parsed.hostname === 'accounts.google.com';
  } catch {
    return false;
  }
}

/** Confidence at or above which an email is offered in "Import all". */
export const BULK_IMPORT_MIN_CONFIDENCE = 0.6;
