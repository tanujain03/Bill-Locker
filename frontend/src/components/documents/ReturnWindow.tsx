import { PackageOpen, Undo2 } from 'lucide-react';
import { formatDate } from '../../lib/document-form';
import { returnText, returnWindow } from '../../lib/returns';

/**
 * A short "Return by 18 Oct" pill for lists, shown only while the window is open: brand-blue
 * while there's time, amber in the last 3 days. Icon + words, never colour alone.
 */
export function ReturnBadge({ returnByDate }: { returnByDate: string | null }) {
  if (!returnByDate) return null;
  const { state, daysLeft } = returnWindow(returnByDate);
  if (state === 'closed') return null;
  const closing = state === 'closing';
  return (
    <span
      title={returnText(returnByDate)}
      className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ${
        closing ? 'bg-amber-50 text-amber-800 ring-amber-200' : 'bg-sky-50 text-sky-800 ring-sky-200'
      }`}
    >
      <Undo2 className="size-3.5" aria-hidden />
      {daysLeft === 0 ? 'Return today' : `Return by ${formatDate(returnByDate).replace(/ \d{4}$/, '')}`}
    </span>
  );
}

/** The bill page's line about returning it: open, closing (amber) or closed (grey). */
export function ReturnBanner({ returnByDate }: { returnByDate: string | null }) {
  if (!returnByDate) return null;
  const { state } = returnWindow(returnByDate);
  const tone =
    state === 'closed'
      ? 'border-slate-200 bg-slate-50 text-slate-700'
      : state === 'closing'
        ? 'border-amber-200 bg-amber-50 text-amber-900'
        : 'border-sky-200 bg-sky-50 text-sky-900';
  return (
    <div className={`flex items-center gap-3 rounded-xl border px-4 py-3 text-sm ${tone}`}>
      <PackageOpen className="size-5 shrink-0" aria-hidden />
      <p>
        <span className="font-semibold">{returnText(returnByDate)}.</span>{' '}
        {state === 'closed'
          ? 'It can no longer be returned to the shop; the warranty still applies.'
          : 'Keep the box, tags and accessories until then.'}
      </p>
    </div>
  );
}
