/**
 * JWT persistence. The token lives in localStorage so a demo survives reloads;
 * everything goes through this module so it can later move to an httpOnly
 * cookie without touching callers. The app never renders untrusted HTML, which
 * keeps the XSS surface that localStorage is sensitive to minimal.
 */
const TOKEN_KEY = 'billlocker.auth.token';

export const tokenStorage = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string): void {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Storage unavailable (private mode / blocked): the session lasts until reload.
    }
  },
  clear(): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      // ignore
    }
  },
};

interface JwtPayload {
  sub?: string;
  exp?: number;
}

/** Decodes the (unverified) payload — only used to read `exp` client-side. */
export function decodeJwtPayload(token: string): JwtPayload | null {
  const payload = token.split('.')[1];
  if (!payload) return null;
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    return JSON.parse(atob(padded)) as JwtPayload;
  } catch {
    return null;
  }
}

/** True when the token's `exp` has passed. Opaque tokens are left to the server. */
export function isTokenExpired(token: string, skewSeconds = 30): boolean {
  const exp = decodeJwtPayload(token)?.exp;
  if (typeof exp !== 'number') return false;
  return Date.now() / 1000 >= exp - skewSeconds;
}
