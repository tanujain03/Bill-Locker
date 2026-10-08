import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './App';
import { AuthProvider } from './components/AuthProvider';
import { FeedbackProvider } from './components/FeedbackProvider';
import './index.css';

// The whole app lives inside the <div id="root"> in index.html.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        {/* toast() and confirm() for every page (lib/feedback-context.ts). */}
        <FeedbackProvider>
          <App />
        </FeedbackProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
