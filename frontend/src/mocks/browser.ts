import { setupWorker } from 'msw/browser';
import { DEMO_CREDENTIALS } from '@/lib/config';
import { resetDatabase } from './db';
import { handlers } from './handlers';

export const worker = setupWorker(...handlers);

/** Starts Mock Service Worker. Only `/api` calls are mocked; assets load normally. */
export async function startMockWorker(): Promise<void> {
  await worker.start({
    serviceWorker: { url: '/mockServiceWorker.js' },
    onUnhandledRequest: 'bypass',
    quiet: true,
  });
  console.info(
    `%c[Bill Locker] Mock API active%c — demo login: ${DEMO_CREDENTIALS.email} / ${DEMO_CREDENTIALS.password}. Reset data: __billLocker.resetMockData()`,
    'color:#4f46e5;font-weight:600',
    'color:inherit',
  );
  (window as unknown as { __billLocker: { resetMockData: () => void } }).__billLocker = {
    resetMockData: () => {
      resetDatabase();
      window.location.reload();
    },
  };
}
