import { RefreshCw, TriangleAlert } from 'lucide-react';
import { Button } from './Button';

/** A page-sized "something went wrong" card with a way out: Try again. */
export function ErrorState({ title, message, onRetry }: { title: string; message: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center rounded-2xl border border-slate-200 bg-white px-6 py-16 text-center shadow-xs"
    >
      <span className="grid size-14 place-items-center rounded-2xl bg-rose-50 text-rose-600">
        <TriangleAlert className="size-7" aria-hidden />
      </span>
      <h2 className="mt-4 text-lg font-semibold">{title}</h2>
      <p className="mt-1 max-w-md text-sm text-slate-600">{message}</p>
      <Button icon={RefreshCw} onClick={onRetry} className="mt-6">
        Try again
      </Button>
    </div>
  );
}
