import { LoaderCircle, RefreshCw, TriangleAlert } from 'lucide-react';
import type { ReactNode } from 'react';
import { getErrorMessage } from '@/lib/api-client';
import { cn } from '@/lib/cn';
import { Button } from './Button';

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-slate-200/70', className)} aria-hidden />;
}

export function Spinner({ className, label = 'Loading' }: { className?: string; label?: string }) {
  return (
    <span role="status" className="inline-flex items-center">
      <LoaderCircle className={cn('size-5 animate-spin text-brand-600', className)} aria-hidden />
      <span className="sr-only">{label}</span>
    </span>
  );
}

export function PageLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-sm text-slate-500">
      <Spinner className="size-6" label={label} />
      <span aria-hidden>{label}</span>
    </div>
  );
}

interface EmptyStateProps {
  icon: ReactNode;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  compact?: boolean;
  className?: string;
}

export function EmptyState({ icon, title, description, action, compact = false, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center text-center',
        compact ? 'px-4 py-8' : 'px-6 py-14',
        className,
      )}
    >
      <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 [&>svg]:size-6">
        {icon}
      </div>
      <h3 className="text-base font-semibold text-slate-900">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-5 flex flex-wrap justify-center gap-2">{action}</div>}
    </div>
  );
}

interface ErrorStateProps {
  title?: string;
  error?: unknown;
  onRetry?: () => void;
  compact?: boolean;
  className?: string;
}

export function ErrorState({ title = 'Something went wrong', error, onRetry, compact = false, className }: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center justify-center text-center',
        compact ? 'px-4 py-8' : 'px-6 py-14',
        className,
      )}
    >
      <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
        <TriangleAlert className="size-6" aria-hidden />
      </div>
      <h3 className="text-base font-semibold text-slate-900">{title}</h3>
      <p className="mt-1 max-w-sm text-sm text-slate-500">
        {getErrorMessage(error, 'We could not load this data. Please try again.')}
      </p>
      {onRetry && (
        <Button variant="secondary" size="sm" className="mt-5" onClick={onRetry} leftIcon={<RefreshCw className="size-4" />}>
          Try again
        </Button>
      )}
    </div>
  );
}

interface QueryLike<T> {
  data: T | undefined;
  isPending: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => unknown;
}

interface QueryStateProps<T> {
  query: QueryLike<T>;
  loading?: ReactNode;
  empty?: ReactNode;
  isEmpty?: (data: T) => boolean;
  errorTitle?: string;
  children: (data: T) => ReactNode;
}

/** Renders the loading / error / empty / success branch of a query consistently. */
export function QueryState<T>({ query, loading, empty, isEmpty, errorTitle, children }: QueryStateProps<T>) {
  if (query.isPending) return <>{loading ?? <PageLoader />}</>;
  if (query.isError) return <ErrorState title={errorTitle} error={query.error} onRetry={() => void query.refetch()} />;
  const data = query.data as T;
  if (empty !== undefined && isEmpty?.(data)) return <>{empty}</>;
  return <>{children(data)}</>;
}
