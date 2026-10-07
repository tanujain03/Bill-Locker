import { RefreshCw, TriangleAlert } from 'lucide-react';

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
      <button
        type="button"
        onClick={onRetry}
        className="mt-6 inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
      >
        <RefreshCw className="size-4" aria-hidden />
        Try again
      </button>
    </div>
  );
}
