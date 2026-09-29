const PREFIX = 'billlocker.session.';

/** Per-tab, per-user scratch state (e.g. the assistant conversation). */
export const sessionCache = {
  key(userId: string, name: string): string {
    return `${PREFIX}${userId}.${name}`;
  },
  read<T>(key: string): T | null {
    try {
      const raw = sessionStorage.getItem(key);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
  },
  write(key: string, value: unknown): void {
    try {
      sessionStorage.setItem(key, JSON.stringify(value));
    } catch {
      // Storage full or unavailable — the state simply won't survive a reload.
    }
  },
  /** Called on sign-out so the next user of this tab sees nothing. */
  clearAll(): void {
    try {
      for (const key of Object.keys(sessionStorage)) {
        if (key.startsWith(PREFIX)) sessionStorage.removeItem(key);
      }
    } catch {
      // ignore
    }
  },
};
