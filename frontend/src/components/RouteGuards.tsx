import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../lib/auth-context';

/** Pages inside it need a signed-in user; others go to /login and come back afterwards. */
export function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <FullPageMessage text="Loading…" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

/**
 * Login and sign-up: a signed-in user is sent on — to the page RequireAuth sent
 * them away from, or /home. This is also what moves you on right after you sign in.
 */
export function GuestOnly() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <FullPageMessage text="Loading…" />;
  if (user) return <Navigate to={(location.state as { from?: string } | null)?.from ?? '/home'} replace />;
  return <Outlet />;
}

function FullPageMessage({ text }: { text: string }) {
  return <div className="grid min-h-dvh place-items-center text-sm text-slate-500">{text}</div>;
}
