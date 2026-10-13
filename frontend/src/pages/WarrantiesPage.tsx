import { ExternalLink, FileText, Search, ShieldCheck, Wrench, type LucideIcon } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ErrorState } from '../components/ErrorState';
import { Tab, TabList } from '../components/Tabs';
import { WarrantyStatusPill } from '../components/warranties/WarrantyStatusPill';
import { errorMessage } from '../lib/api';
import { claimKitLink } from '../lib/claims';
import { productLink } from '../lib/products';
import { usePageTitle } from '../lib/usePageTitle';
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
  usePageTitle('Warranties');
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

        <div className="mt-6 flex flex-col-reverse gap-3 lg:flex-row lg:items-end lg:justify-between">
          <TabList label="Warranty status">
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
          </TabList>
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
            <Empty>{urlQuery ? `No products match “${urlQuery}”.` : EMPTY_TAB[status ?? 'ALL']}</Empty>
          ) : (
            <WarrantyRows items={list.items} />
          )}
        </div>
      </main>
    </div>
  );
}

/** What an empty tab says: specific, and good news where it is. */
const EMPTY_TAB: Record<WarrantyStatus | 'ALL', string> = {
  ALL: 'No products on your saved bills yet.',
  EXPIRING_SOON: 'Nothing ends in the next 30 days.',
  ACTIVE: 'No warranties are active right now.',
  EXPIRED: 'No warranties have ended.',
  NO_INFO: 'Every product has a warranty date.',
};

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
            <th className="w-px py-3 pr-3 pl-2">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((w, i) => (
            <tr key={`${w.documentId}-${i}`} className="hover:bg-slate-50">
              <td className="max-w-xs px-4 py-3">
                <Link to={productLink(w.documentId, w.position)} className="block truncate font-medium hover:text-brand-700 hover:underline">
                  {w.productName ?? 'Unnamed product'}
                </Link>
                <ProductMeta warranty={w} />
              </td>
              <td className="max-w-[12rem] truncate px-4 py-3 text-slate-600">{w.sellerName ?? '—'}</td>
              <td className="px-4 py-3 whitespace-nowrap text-slate-600 tabular-nums">
                {w.endDate ? formatDate(w.endDate) : '—'}
              </td>
              <td className="px-4 py-3 whitespace-nowrap">
                <WarrantyStatusPill status={w.status} daysLeft={w.daysLeft} />
              </td>
              <td className="w-px py-3 pr-3 pl-2 text-right whitespace-nowrap">
                <RowLinks warranty={w} />
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
                <Link to={productLink(w.documentId, w.position)} className="block truncate font-medium hover:text-brand-700 hover:underline">
                  {w.productName ?? 'Unnamed product'}
                </Link>
                <p className="truncate text-sm text-slate-500">
                  {[w.sellerName, w.endDate && `Ends ${formatDate(w.endDate)}`].filter(Boolean).join(' · ')}
                </p>
              </div>
              <WarrantyStatusPill status={w.status} daysLeft={w.daysLeft} />
            </div>
            <div className="mt-2 flex items-center justify-between gap-3">
              <ProductMeta warranty={w} />
              <RowLinks warranty={w} />
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
  return meta ? <p className="truncate text-xs text-slate-600">{meta}</p> : <span />;
}

/**
 * The row's three actions as square icon buttons: Claim (the claim kit), Register (the
 * brand's registration page, new tab) and Open bill. Each has a tooltip on hover and keyboard
 * focus, and a label for screen readers. Without a registration page the Register icon stays
 * in place but faded and not clickable, so the icons line up in the same columns on every row.
 */
function RowLinks({ warranty: w }: { warranty: WarrantyView }) {
  const product = w.productName ?? 'this product';
  const canRegister = Boolean(w.registrationUrl && /^https?:\/\//i.test(w.registrationUrl));
  return (
    <span className="inline-flex shrink-0 items-center gap-1.5">
      <IconAction
        to={claimKitLink(w.documentId, w.position)}
        icon={Wrench}
        tip="Claim kit"
        label={`Claim kit for ${product}`}
        tone="text-amber-700 hover:bg-amber-50 hover:border-amber-300"
      />
      {canRegister ? (
        <IconAction
          href={w.registrationUrl!}
          icon={ExternalLink}
          tip="Register warranty"
          label={`Register the warranty of ${product} on the brand's site (opens a new tab)`}
          tone="text-emerald-700 hover:bg-emerald-50 hover:border-emerald-300"
        />
      ) : (
        <span
          title="No registration page saved for this product"
          aria-hidden
          className="grid size-8 place-items-center rounded-lg border border-slate-200 text-slate-300"
        >
          <ExternalLink className="size-4" />
        </span>
      )}
      <IconAction
        to={`/documents/${w.documentId}`}
        icon={FileText}
        tip="Open bill"
        label={`Open the bill of ${product}`}
        tone="text-brand-700 hover:bg-brand-50 hover:border-brand-200"
      />
    </span>
  );
}

/** One square icon button: a link inside the app (`to`) or a page in a new tab (`href`). */
function IconAction(props: { to?: string; href?: string; icon: LucideIcon; tip: string; label: string; tone: string }) {
  const Icon = props.icon;
  const className = `group relative grid size-8 place-items-center rounded-lg border border-slate-200 bg-white transition ${props.tone}`;
  const body = (
    <>
      <Icon className="size-4" aria-hidden />
      {/* Tooltip: pure CSS, above the button, on hover and on keyboard focus. */}
      <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1.5 hidden -translate-x-1/2 rounded-md bg-slate-900 px-2 py-1 text-xs font-medium whitespace-nowrap text-white shadow-lg group-hover:block group-focus-visible:block">
        {props.tip}
      </span>
    </>
  );
  return props.href ? (
    <a href={props.href} target="_blank" rel="noopener noreferrer" aria-label={props.label} className={className}>
      {body}
    </a>
  ) : (
    <Link to={props.to!} aria-label={props.label} className={className}>
      {body}
    </Link>
  );
}
