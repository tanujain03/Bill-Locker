import { ArrowRight, Search, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ErrorState } from '../components/ErrorState';
import { WarrantyStatusPill } from '../components/warranties/WarrantyStatusPill';
import { errorMessage } from '../lib/api';
import { formatDate } from '../lib/document-form';
import {
  listWarranties,
  WARRANTY_STATUS_LABELS,
  WARRANTY_STATUSES,
  type WarrantyCounts,
  type WarrantyList,
  type WarrantyStatus,
  type WarrantyView,
} from '../lib/warranties';

const COUNT_KEY: Record<WarrantyStatus, keyof WarrantyCounts> = {
  ACTIVE: 'active',
  EXPIRING_SOON: 'expiringSoon',
  EXPIRED: 'expired',
  NO_INFO: 'noInfo',
};

/** /warranties: every product on your saved bills and how long its warranty lasts. */
export function WarrantiesPage() {
  // The tab and the search live in the URL, so the dashboard can link straight to a tab.
  const [params, setParams] = useSearchParams();
  const status = (WARRANTY_STATUSES as string[]).includes(params.get('status') ?? '')
    ? (params.get('status') as WarrantyStatus)
    : undefined;
  const urlQuery = params.get('q') ?? '';
  const [search, setSearch] = useState(urlQuery);

  const [list, setList] = useState<WarrantyList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const latest = useRef(0); // only the newest answer may update the page

  const load = useCallback(() => {
    const mine = ++latest.current;
    setError(null);
    listWarranties({ status, q: urlQuery })
      .then((answer) => mine === latest.current && setList(answer))
      .catch((e) => mine === latest.current && setError(errorMessage(e)));
  }, [status, urlQuery]);

  useEffect(load, [load]);

  // When the URL changes from outside (a header or dashboard link), the box follows it.
  useEffect(() => setSearch(urlQuery), [urlQuery]);

  // Typing updates the URL 300 ms after the last key, which then reloads the list.
  useEffect(() => {
    if (search === urlQuery) return;
    const timer = setTimeout(() => setParam('q', search), 300);
    return () => clearTimeout(timer);
  }, [search, urlQuery]);

  function setParam(name: string, value: string | undefined) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (value) next.set(name, value);
        else next.delete(name);
        return next;
      },
      { replace: true },
    );
  }

  const counts = list?.counts;
  const noSavedBills = counts?.all === 0 && !urlQuery;

  return (
    <div className="min-h-dvh">
      <main className="mx-auto max-w-6xl px-4 py-8">
        <h1 className="text-2xl font-semibold tracking-tight">Warranties</h1>
        <p className="mt-1 text-slate-600">Every product on your saved bills and how long its warranty lasts.</p>

        <div className="mt-6 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <nav aria-label="Warranty status" className="-mx-1 flex gap-1 overflow-x-auto px-1 pb-1">
            <Tab label="All" count={counts?.all} active={!status} onClick={() => setParam('status', undefined)} />
            {WARRANTY_STATUSES.map((s) => (
              <Tab
                key={s}
                label={WARRANTY_STATUS_LABELS[s]}
                count={counts?.[COUNT_KEY[s]]}
                active={status === s}
                onClick={() => setParam('status', s)}
              />
            ))}
          </nav>
          <label className="relative lg:w-80">
            <span className="sr-only">Search warranties</span>
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search product, model, serial or shop"
              className="block w-full rounded-lg border border-slate-300 bg-white py-2 pr-3 pl-9 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
            />
          </label>
        </div>

        <div className="mt-4">
          {error ? (
            <ErrorState title="Could not load your warranties" message={error} onRetry={load} />
          ) : !list ? (
            <div className="animate-pulse space-y-2" aria-label="Loading warranties">
              {[1, 2, 3, 4].map((n) => (
                <div key={n} className="h-16 rounded-xl bg-slate-200" />
              ))}
            </div>
          ) : noSavedBills ? (
            <Empty>
              No saved bills yet. Save a bill with its warranty details to see it here.{' '}
              <Link to="/documents" className="font-medium text-brand-700 hover:underline">
                Go to documents
              </Link>
            </Empty>
          ) : list.items.length === 0 ? (
            <Empty>Nothing here.</Empty>
          ) : (
            <WarrantyRows items={list.items} />
          )}
        </div>
      </main>
    </div>
  );
}

function Tab(props: { label: string; count: number | undefined; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={props.onClick}
      aria-pressed={props.active}
      className={`inline-flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium ${
        props.active ? 'bg-brand-600 text-white' : 'bg-white text-slate-700 ring-1 ring-slate-200 hover:bg-slate-50'
      }`}
    >
      {props.label}
      {props.count !== undefined && (
        <span
          className={`rounded-full px-1.5 text-xs tabular-nums ${props.active ? 'bg-white/20' : 'bg-slate-100 text-slate-600'}`}
        >
          {props.count}
        </span>
      )}
    </button>
  );
}

function Empty({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col items-center rounded-xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
      <span className="grid size-10 place-items-center rounded-xl bg-brand-50 text-brand-600">
        <ShieldCheck className="size-5" aria-hidden />
      </span>
      <p className="mt-3 max-w-md text-sm text-slate-600">{children}</p>
    </div>
  );
}

/** A table on wider screens, cards on phones — same data, same links. */
function WarrantyRows({ items }: { items: WarrantyView[] }) {
  return (
    <>
      <table className="hidden w-full overflow-hidden rounded-xl bg-white text-left text-sm ring-1 ring-slate-200 md:table">
        <thead className="bg-slate-50 text-xs font-medium tracking-wide text-slate-500 uppercase">
          <tr>
            <th className="px-4 py-3">Product</th>
            <th className="px-4 py-3">Shop</th>
            <th className="px-4 py-3">Ends</th>
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3">
              <span className="sr-only">Bill</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((w, i) => (
            <tr key={`${w.documentId}-${i}`} className="hover:bg-slate-50">
              <td className="max-w-xs px-4 py-3">
                <p className="truncate font-medium">{w.productName ?? 'Unnamed product'}</p>
                <ProductMeta warranty={w} />
              </td>
              <td className="max-w-[12rem] truncate px-4 py-3 text-slate-600">{w.sellerName ?? '—'}</td>
              <td className="px-4 py-3 whitespace-nowrap text-slate-600 tabular-nums">
                {w.endDate ? formatDate(w.endDate) : '—'}
              </td>
              <td className="px-4 py-3">
                <WarrantyStatusPill status={w.status} daysLeft={w.daysLeft} />
              </td>
              <td className="px-4 py-3 text-right">
                <BillLink id={w.documentId} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <ul className="space-y-2 md:hidden">
        {items.map((w, i) => (
          <li key={`${w.documentId}-${i}`} className="rounded-xl bg-white p-4 ring-1 ring-slate-200">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate font-medium">{w.productName ?? 'Unnamed product'}</p>
                <p className="truncate text-sm text-slate-500">
                  {[w.sellerName, w.endDate && `Ends ${formatDate(w.endDate)}`].filter(Boolean).join(' · ')}
                </p>
              </div>
              <WarrantyStatusPill status={w.status} daysLeft={w.daysLeft} />
            </div>
            <div className="mt-2 flex items-center justify-between gap-3">
              <ProductMeta warranty={w} />
              <BillLink id={w.documentId} />
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}

function ProductMeta({ warranty: w }: { warranty: WarrantyView }) {
  const meta = [w.modelNumber && `Model ${w.modelNumber}`, w.serialNumber && `S/N ${w.serialNumber}`, w.warrantyProvider]
    .filter(Boolean)
    .join(' · ');
  return meta ? <p className="truncate text-xs text-slate-500">{meta}</p> : <span />;
}

function BillLink({ id }: { id: string }) {
  return (
    <Link
      to={`/documents/${id}`}
      className="inline-flex shrink-0 items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-800"
    >
      Open bill
      <ArrowRight className="size-4" aria-hidden />
    </Link>
  );
}
