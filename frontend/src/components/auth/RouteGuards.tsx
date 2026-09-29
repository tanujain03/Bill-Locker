import { CloudOff } from 'lucide-react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { Button } from '@/components/ui/Button';
import { PageLoader } from '@/components/ui/feedback';
import { LogoMark } from '@/components/ui/Logo';
import { useAuth } from '@/lib/auth-context';

function FullScreenLoader() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4">
      <LogoMark className="size-10" />
      <PageLoader label="Opening your locker…" />
    </div>
  );
}

function FullScreenError({ onRetry, onSignOut }: { onRetry: () => void; onSignOut: () => void }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 px-6 text-center">
      <div className="flex size-12 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
        <CloudOff className="size-6" aria-hidden />
      </div>
      <h1 className="text-lg font-semibold text-slate-900">We can’t reach Bill Locker right now</h1>
      <p className="max-w-sm text-sm text-slate-500">Check your connection or try again in a moment.</p>
      <div className="mt-2 flex gap-2">
        <Button onClick={onRetry}>Try again</Button>
        <Button variant="secondary" onClick={onSignOut}>
          Sign out
        </Button>
      </div>
    </div>
  );
}

/** Only for signed-in users; everyone else is sent to /login and brought back afterwards. */
export function ProtectedRoute() {
  const { status, retry, logout } = useAuth();
  const location = useLocation();

  if (status === 'loading') return <FullScreenLoader />;
  if (status === 'error') return <FullScreenError onRetry={retry} onSignOut={logout} />;
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }
  return <Outlet />;
}

function safeRedirect(from: unknown): string {
  // Only same-origin, absolute paths — never `//evil.example` or full URLs.
  if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')) return from;
  return '/dashboard';
}

/** Login/register: signed-in users go straight to where they were heading. */
export function PublicOnlyRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') return <FullScreenLoader />;
  if (status === 'authenticated') {
    const from = (location.state as { from?: unknown } | null)?.from;
    return <Navigate to={safeRedirect(from)} replace />;
  }
  return <Outlet />;
}
