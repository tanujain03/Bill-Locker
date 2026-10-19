import { PriceComparisonPage } from './pages/PriceComparisonPage';
import { SplitsPage } from './pages/SplitsPage';
import { SplitGroupPage } from './pages/SplitGroupPage';
import { SplitInvitePage } from './pages/SplitInvitePage';
import { Navigate, Route, Routes, useParams } from 'react-router';
import { ServicesPage } from './pages/ServicesPage';
import { AppLayout } from './components/AppLayout';
import { GmailPage } from './pages/GmailPage';
import { GuestOnly, RequireAuth } from './components/RouteGuards';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { DocumentPage } from './pages/DocumentPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { BillsPage } from './pages/BillsPage';
import { ClaimKitPage } from './pages/ClaimKitPage';
import { ProductPage } from './pages/ProductPage';
import { DashboardPage } from './pages/DashboardPage';
import { ExpensesPage } from './pages/ExpensesPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { WarrantiesPage } from './pages/WarrantiesPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { SettingsPage } from './pages/SettingsPage';

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
      <Route path="/split-invite" element={<SplitInvitePage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      {/* Only for signed-in users; others are sent to /login. */}
      <Route element={<RequireAuth />}>
        {/* Every signed-in page sits in the same frame: navigation on the left. */}
        <Route element={<AppLayout />}>
          <Route path="/home" element={<DashboardPage />} />
          <Route path="/compare-prices" element={<PriceComparisonPage />} />
          <Route path="/expenses" element={<ExpensesPage />} />
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/gmail" element={<GmailPage />} />
          <Route path="/splits" element={<SplitsPage />} />
          <Route path="/splits/:groupId" element={<SplitGroupPage />} />
          <Route path="/services" element={<ServicesPage />} />
          <Route path="/warranties" element={<WarrantiesPage />} />
          <Route path="/warranties/claim/:productId" element={<ClaimKitPage />} />
          {/* Older positional links cannot identify a product safely after list edits. */}
          <Route path="/products/:documentId/:position" element={<LegacyProductLink />} />
          <Route path="/warranties/claim/:documentId/:position" element={<LegacyProductLink />} />
          <Route path="/products/:productId" element={<ProductPage />} />
          <Route path="/bills" element={<BillsPage />} />
          <Route path="/documents/:id" element={<DocumentPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

/** Take old bookmarks to their bill so they cannot silently open a different product. */
function LegacyProductLink() {
  const { documentId } = useParams();
  return <Navigate to={`/documents/${documentId}`} replace />;
}
