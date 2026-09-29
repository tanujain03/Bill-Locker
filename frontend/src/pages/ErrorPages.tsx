import { Compass, TriangleAlert } from 'lucide-react';
import { isRouteErrorResponse, useRouteError } from 'react-router';
import { Button, ButtonLink } from '@/components/ui/Button';
import { LogoMark } from '@/components/ui/Logo';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';

export function NotFoundPage() {
  useDocumentTitle('Page not found');
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 px-6 text-center">
      <LogoMark className="mb-4 size-10" />
      <Compass className="size-8 text-slate-300" aria-hidden />
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Page not found</h1>
      <p className="max-w-sm text-sm text-slate-500">The page you’re looking for doesn’t exist or has moved.</p>
      <ButtonLink to="/dashboard" className="mt-3">
        Go to dashboard
      </ButtonLink>
    </div>
  );
}

/** Router error boundary: an unexpected render error never leaves a blank screen. */
export function RouteErrorPage() {
  const error = useRouteError();
  const message = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : error instanceof Error
      ? error.message
      : 'An unexpected error occurred.';

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 px-6 text-center" role="alert">
      <div className="flex size-12 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
        <TriangleAlert className="size-6" aria-hidden />
      </div>
      <h1 className="text-xl font-semibold text-slate-900">Something went wrong</h1>
      <p className="max-w-md text-sm text-slate-500">{message}</p>
      <div className="mt-3 flex gap-2">
        <Button onClick={() => window.location.reload()}>Reload page</Button>
        <ButtonLink to="/dashboard" variant="secondary" reloadDocument>
          Go to dashboard
        </ButtonLink>
      </div>
    </div>
  );
}
