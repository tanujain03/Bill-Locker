import { CalendarDays, Receipt, Search, Wallet, type LucideIcon } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { DocumentList } from '../components/documents/DocumentList';
import { ErrorState } from '../components/ErrorState';
import { errorMessage } from '../lib/api';
import { BILL_CATEGORY_LABELS, formatAmount, listDocuments, type BillCategory, type DocumentSummary } from '../lib/documents';
import { usePageTitle } from '../lib/usePageTitle';

/**
 * /bills: the documents of type "Bill / receipt" (RECEIPT): rides, food, fuel, utility and
 * phone bills — everyday spending without a product warranty. The AI files a bill here when
 * it reads it; changing a document's type on its page moves it in or out.
 */
export function BillsPage() {
  usePageTitle('Bills & receipts');
  // The search lives in the URL, like on the Documents page (Back and refresh keep it).
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  /** A category chip (filtered here: the whole list is already loaded). */
  const category = params.get('category') ?? '';
  const setParam = (name: string, value: string) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (value) next.set(name, value);
        else next.delete(name);
        return next;
      },
      { replace: true },
    );
  const [bills, setBills] = useState<DocumentSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const latest = useRef(0); // only the newest answer may update the page

  const load = useCallback(() => {
    const mine = ++latest.current;
    setError(null);
    listDocuments({ q, type: 'RECEIPT' })
      .then((list) => mine === latest.current && setBills(list))
      .catch((e) => mine === latest.current && setError(errorMessage(e)));
  }, [q]);

  // The search waits 300 ms after the last key press, so we don't ask for every letter.
  useEffect(() => {
    const timer = setTimeout(load, 300);
    return () => clearTimeout(timer);
  }, [load]);

  const thisMonth = new Date().toISOString().slice(0, 7); // "2026-10"
  const sum = (list: DocumentSummary[]) => list.reduce((total, b) => total + (b.totalAmount ?? 0), 0);
  // Only the categories that have bills get a chip, each with its count.
  const counts = new Map<BillCategory, number>();
  for (const b of bills ?? []) if (b.category) counts.set(b.category, (counts.get(b.category) ?? 0) + 1);
  const shown = bills && category ? bills.filter((b) => b.category === category) : bills;

  return (
    <main className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-2xl font-semibold tracking-tight">Bills & receipts</h1>
      <p className="mt-1 text-slate-600">
        Rides, food, fuel, utility and phone bills: your everyday spending, kept apart from products with a warranty.
      </p>

      {shown && bills && bills.length > 0 && (
        <div className="mt-6 grid gap-4 sm:grid-cols-3">
          <Stat
            icon={Receipt}
            label={q || category ? 'Matching bills' : 'Bills & receipts'}
            value={String(shown.length)}
          />
          <Stat
            icon={CalendarDays}
            label="Spent this month"
            value={formatAmount(sum(shown.filter((b) => b.purchaseDate?.startsWith(thisMonth))), { whole: true })}
          />
          <Stat
            icon={Wallet}
            label={q || category ? 'Total of these' : 'Total'}
            value={formatAmount(sum(shown), { whole: true })}
          />
        </div>
      )}

      {counts.size > 0 && (
        <div className="mt-6 flex flex-wrap gap-2" role="group" aria-label="Category">
          <Chip label="All" count={bills?.length ?? 0} active={!category} onClick={() => setParam('category', '')} />
          {(Object.keys(BILL_CATEGORY_LABELS) as BillCategory[])
            .filter((c) => counts.has(c))
            .map((c) => (
              <Chip
                key={c}
                label={BILL_CATEGORY_LABELS[c]}
                count={counts.get(c) ?? 0}
                active={category === c}
                onClick={() => setParam('category', c)}
              />
            ))}
        </div>
      )}

      <label className="relative mt-6 block sm:max-w-md">
        <span className="sr-only">Search bills and receipts</span>
        <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
        <input
          type="search"
          value={q}
          onChange={(e) => setParam('q', e.target.value)}
          placeholder="Search shop, number or file name"
          className="block w-full rounded-lg border border-slate-300 bg-white py-2 pr-3 pl-9 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
        />
      </label>

      {error ? (
        <div className="mt-4">
          <ErrorState title="Could not load your bills" message={error} onRetry={load} />
        </div>
      ) : !shown ? (
        <div className="mt-4 animate-pulse space-y-px overflow-hidden rounded-xl" aria-label="Loading bills">
          {[1, 2, 3, 4].map((n) => (
            <div key={n} className="h-16 bg-slate-200" />
          ))}
        </div>
      ) : (
        <DocumentList
          documents={shown}
          filtered={Boolean(q || category)}
          emptyText="No bills or receipts yet. Upload one (top right): rides, food, fuel, utility and phone bills are filed here automatically."
        />
      )}
    </main>
  );
}

/** A category filter: filled when chosen, with how many bills it has. */
function Chip(props: { label: string; count: number; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={props.onClick}
      aria-pressed={props.active}
      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-medium ring-1 ${
        props.active
          ? 'bg-brand-600 text-white ring-brand-600'
          : 'bg-white text-slate-700 ring-slate-200 hover:bg-slate-50'
      }`}
    >
      {props.label}
      <span className={`text-xs tabular-nums ${props.active ? 'text-white/80' : 'text-slate-500'}`}>{props.count}</span>
    </button>
  );
}

function Stat({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <div className="flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
      <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand-600">
        <Icon className="size-5" aria-hidden />
      </span>
      <span className="min-w-0">
        <span className="block text-sm font-medium text-slate-500">{label}</span>
        <span className="mt-0.5 block truncate text-2xl font-semibold tracking-tight tabular-nums">{value}</span>
      </span>
    </div>
  );
}
