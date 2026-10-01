import { addDays, toISODate } from '@/utils/date';

export function newId(prefix: string): string {
  const random =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return `${prefix}_${random}`;
}

export function nowIso(): string {
  return new Date().toISOString();
}

/** ISO date `days` before today (negative = in the future). */
export function daysAgo(days: number, today = new Date()): string {
  return toISODate(addDays(today, -days));
}

/** ISO timestamp `hours` before now. */
export function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 3_600_000).toISOString();
}

/** Deterministic non-cryptographic hash (cyrb53) — the real backend uses BCrypt. */
export function hashPassword(password: string): string {
  let h1 = 0xdeadbeef ^ 17;
  let h2 = 0x41c6ce57 ^ 17;
  for (let index = 0; index < password.length; index += 1) {
    const code = password.charCodeAt(index);
    h1 = Math.imul(h1 ^ code, 2654435761);
    h2 = Math.imul(h2 ^ code, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return `mock$${(4294967296 * (2097151 & h2) + (h1 >>> 0)).toString(36)}`;
}
