import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest';
import { resetDatabase } from '@/mocks/db';
import { server } from '@/mocks/server';

const hasDom = typeof window !== 'undefined';

// Pages are lazy-loaded chunks; give `findBy*` room when the suite runs in parallel.
configure({ asyncUtilTimeout: 5000 });

// jsdom gaps used by the UI (skipped for `@vitest-environment node` suites).
if (hasDom) {
  if (typeof window.matchMedia !== 'function') {
    window.matchMedia = (query: string) =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }) as MediaQueryList;
  }
  if (!Element.prototype.scrollIntoView) Element.prototype.scrollIntoView = () => {};
  if (!('ResizeObserver' in globalThis)) {
    (globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  }
  if (!URL.createObjectURL) URL.createObjectURL = () => 'blob:mock';
  if (!URL.revokeObjectURL) URL.revokeObjectURL = () => {};
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

beforeEach(() => {
  if (hasDom) {
    localStorage.clear();
    sessionStorage.clear();
  }
  resetDatabase();
});

afterEach(() => {
  if (hasDom) cleanup();
  server.resetHandlers();
});

afterAll(() => server.close());
