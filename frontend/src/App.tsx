import { Navigate, Route, Routes } from 'react-router';
import { AppLayout } from './components/AppLayout';
import { GmailPage } from './pages/GmailPage';
import { GuestOnly, RequireAuth } from './components/RouteGuards';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { DocumentPage } from './pages/DocumentPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { WarrantiesPage } from './pages/WarrantiesPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';

/** Which page shows for which URL. */
export function App() {
  return (
    <Routes>
      {/* Signed-in users skip these and go to /home. The first page is sign in. */}
      <Route element={<GuestOnly />}>
        <Route path="/" element={<LoginPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>

      {/* Open to everyone: a reset link must work even if you're signed in. */}
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      {/* Only for signed-in users; others are sent to /login. */}
      <Route element={<RequireAuth />}>
        {/* Every signed-in page sits in the same frame: navigation on the left. */}
        <Route element={<AppLayout />}>
          <Route path="/home" element={<DashboardPage />} />
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/gmail" element={<GmailPage />} />
          <Route path="/warranties" element={<WarrantiesPage />} />
          <Route path="/documents/:id" element={<DocumentPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
