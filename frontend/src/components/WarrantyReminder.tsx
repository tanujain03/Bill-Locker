import { Clock, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../lib/auth-context';
import type { WarrantyView } from '../lib/warranties';

/** The day the reminder was last shown, per user, in this browser (a per-viewer convenience). */
const shownKey = (userId: string) => `billLocker.warrantyReminder.${userId}`;

const today = () => new Date().toLocaleDateString('en-CA'); // "2026-10-12", in the user's own time zone

function shownToday(userId: string): boolean {
  try {
    return localStorage.getItem(shownKey(userId)) === today();
  } catch {
    return false; // storage blocked: show it, it can still be closed
  }
}

function rememberShown(userId: string) {
  try {
    localStorage.setItem(shownKey(userId), today());
  } catch {
    // not saved: it may show again after a reload, that's all
  }
}

const endsIn = (days: number | null) =>
  days === 0 ? 'ends today' : days === 1 ? 'ends tomorrow' : `ends in ${days} days`;

/**
 * Once a day, a small card in the corner: "these warranties end soon". The list comes
 * from the 🔔 numbers (useAttention: warranties ending in the next 30 days). Shown at
 * most once a day per user; ✕ or any link closes it.
 */
export function WarrantyReminder({ expiring }: { expiring: WarrantyView[] | undefined }) {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!user || !expiring?.length || shownToday(user.id)) return;
    rememberShown(user.id); // counted as shown once it appears, even if never closed
    setOpen(true);
  }, [user, expiring]);

  if (!open || !expiring?.length) return null;
  const close = () => setOpen(false);
  const many = expiring.length === 1 ? '1 warranty ends' : `${expiring.length} warranties end`;

  return (
    <aside
      aria-label="Warranty reminder"
      className="fixed inset-x-4 top-20 z-40 rounded-xl border border-amber-200 bg-white p-4 shadow-lg sm:inset-x-auto sm:right-6 sm:w-88"
    >
      <div className="flex items-start gap-3">
        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-amber-50 text-amber-600">
          <Clock className="size-5" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-slate-900">Warranties ending soon</p>
          <p className="text-sm text-slate-600">{many} in the next 30 days.</p>
        </div>
        <button
          type="button"
          onClick={close}
          aria-label="Close reminder"
          className="-m-1 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
        >
          <X className="size-4" aria-hidden />
        </button>
      </div>

      <ul className="mt-3 space-y-1">
        {expiring.slice(0, 3).map((w) => (
          <li key={`${w.documentId}-${w.productName}`}>
            <Link
              to={`/documents/${w.documentId}`}
              onClick={close}
              className="flex items-center justify-between gap-3 rounded-lg px-2 py-1.5 text-sm hover:bg-slate-50"
            >
              <span className="min-w-0 truncate text-slate-800">{w.productName ?? 'A product'}</span>
              <span className="shrink-0 text-xs font-medium text-amber-700">{endsIn(w.daysLeft)}</span>
            </Link>
          </li>
        ))}
      </ul>

      <Link
        to="/warranties?status=EXPIRING_SOON"
        onClick={close}
        className="mt-2 block rounded-lg px-2 py-1.5 text-sm font-medium text-brand-700 hover:bg-brand-50"
      >
        See all warranties →
      </Link>
    </aside>
  );
}
