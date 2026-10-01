import '@fontsource-variable/inter';
import './index.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { config } from './lib/config';

/** Starts the in-browser mock API (Mock Service Worker) when VITE_API_MOCKING=true. */
async function enableMocking(): Promise<void> {
  if (!config.apiMocking) return;
  const { startMockWorker } = await import('./mocks/browser');
  await startMockWorker();
}

const container = document.getElementById('root');
if (!container) throw new Error('Root element #root not found');

enableMocking()
  .catch((error: unknown) => {
    console.error('[Bill Locker] Could not start the mock API', error);
  })
  .finally(() => {
    createRoot(container).render(
      <StrictMode>
        <App />
      </StrictMode>,
    );
  });
