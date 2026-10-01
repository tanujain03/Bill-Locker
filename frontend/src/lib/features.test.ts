import { afterEach, describe, expect, it, vi } from 'vitest';

/**
 * Loads fresh copies of the modules as if the app talked to the real backend
 * (tests otherwise run with the mock API, and the setup file has already loaded them).
 */
async function withRealBackend() {
  vi.resetModules();
  vi.doMock('@/lib/config', () => ({ config: { apiMocking: false } }));
  const features = await import('./features');
  const labels = await import('@/utils/labels');
  return { ...features, ...labels };
}

afterEach(() => {
  vi.doUnmock('@/lib/config');
  vi.resetModules();
});

describe('features against the real backend', () => {
  it('shows only what the backend already serves', async () => {
    const { homePath, isFeatureEnabled } = await withRealBackend();

    expect(isFeatureEnabled('dashboard')).toBe(false);
    expect(isFeatureEnabled('gmail')).toBe(false);
    expect(isFeatureEnabled('documentProcessing')).toBe(false);
    expect(homePath()).toBe('/documents');
  });

  it('treats an uploaded document as stored, not as still processing', async () => {
    const { isProcessing, isStoredOnly } = await withRealBackend();

    expect(isProcessing('UPLOADED')).toBe(false);
    expect(isStoredOnly('UPLOADED')).toBe(true);
    expect(isProcessing('PROCESSING')).toBe(true);
  });
});
