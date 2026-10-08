import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../lib/auth-context';

/**
 * Pages inside it need a signed-in user; others go to /login and come back afterwards.
 * The login page says why: "signed out" (no way back: you meant to leave), or
 * "session expired" (back to the same page after signing in again).
 */
export function RequireAuth() {
  const { user, loading, signedOutReason } = useAuth();
  const location = useLocation();
  if (loading) return <FullPageMessage text="Loading…" />;
  if (!user) {
    const from = location.pathname + location.search;
    const state =
      signedOutReason === 'signedOut'
        ? { message: 'You’ve been signed out.' }
        : signedOutReason === 'expired'
          ? { from, info: 'Your session expired. Please sign in again.' }
          : { from };
    return <Navigate to="/login" replace state={state} />;
  }
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
