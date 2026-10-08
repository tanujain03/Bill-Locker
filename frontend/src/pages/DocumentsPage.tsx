import { CalendarDays, FilterX, Search, Upload, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { FileTypeIcon, SourceBadge } from '../components/documents/SourceBadge';
import { StatusBadge } from '../components/documents/StatusBadge';
import { Button } from '../components/Button';
import { UploadButton } from '../components/UploadButton';
import { Alert } from '../components/FormParts';
import { errorMessage } from '../lib/api';
import {
  DOCUMENT_TYPE_LABELS,
  formatAmount,
  listDocuments,
  monthLabel,
  type DocumentStatus,
  type DocumentSummary,
} from '../lib/documents';
import { usePolling } from '../lib/usePolling';
import { usePageTitle } from '../lib/usePageTitle';

/** /documents: upload a bill, and the list of your bills with search and filters. */
export function DocumentsPage() {
  usePageTitle('Documents');

  // The filters live in the URL (?q=&type=&status=&source=&month=), so a link from the
  // dashboard opens the list already filtered, and Back/refresh keep the filters.
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const type = params.get('type') ?? '';
  const status = params.get('status') ?? '';
  /** '' (all), 'UPLOAD' or 'GMAIL'. Filtered here: the list is already loaded. */
  const source = params.get('source') ?? '';
  /** 'YYYY-MM': bills bought in that month (filtered here, like source). */
  const monthParam = params.get('month') ?? '';
  const month = /^\d{4}-\d{2}$/.test(monthParam) ? monthParam : ''; // ignore a malformed ?month=
  const setFilter = (name: string) => (value: string) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (value) next.set(name, value);
        else next.delete(name);
        return next;
      },
      { replace: true }, // typing in the search box shouldn't fill the Back history
    );
  const [documents, setDocuments] = useState<DocumentSummary[] | null>(null);
  const [listError, setListError] = useState<string | null>(null);

  // Reload the list when a filter changes. The search waits 300 ms after the last
  // key press, so we don't call the backend for every letter.
  useEffect(() => {
    let current = true; // ignore answers that arrive after a newer request started
    const timer = setTimeout(() => {
      listDocuments({ q, type, status })
        .then((list) => {
          if (!current) return;
          setDocuments(list);
          setListError(null);
        })
        .catch((error) => {
          if (current) setListError(errorMessage(error));
        });
    }, 300);
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [q, type, status]);

  // While a document waits for the background AI read, ask again every 3 s with the same
  // filters, so "Reading…" turns into "Needs review" without a reload.
  usePolling(documents?.some((d) => d.readQueued) ?? false, () => {
    listDocuments({ q, type, status })
      .then(setDocuments)
      .catch(() => {}); // the next tick tries again
  });

  const filtered = Boolean(q || type || status || source || month);
  // First visit (no bills at all): a big "add your first bill" area. After that the list
  // is what people come for, so uploading shrinks to one slim line above it.
  const noBillsYet = documents?.length === 0 && !filtered;

  return (
    <div className="min-h-dvh">
      <main className="mx-auto max-w-6xl px-4 py-8">
        <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
        <p className="mt-1 text-slate-600">Upload invoices, warranty cards and receipts. We read the details for you.</p>

        {noBillsYet ? (
          <div className="mt-6 flex flex-col items-center rounded-2xl border-2 border-dashed border-slate-300 bg-white px-6 py-10 text-center">
            <span className="grid size-12 place-items-center rounded-xl bg-brand-50 text-brand-600">
              <Upload className="size-6" aria-hidden />
            </span>
            <p className="mt-3 font-medium">Add your first bill</p>
            <p className="mt-1 text-sm text-slate-500">
              Drop a file anywhere on this page, or choose one. PDF, JPG, PNG or WebP, up to 10 MB.
            </p>
            <UploadButton className="mt-4">Choose file</UploadButton>
          </div>
        ) : (
          <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-dashed border-slate-300 bg-white px-4 py-3">
            <p className="flex items-center gap-2 text-sm text-slate-600">
              <Upload className="size-4 shrink-0 text-brand-600" aria-hidden />
              Drop a bill anywhere on the page to upload it (PDF, JPG, PNG or WebP, up to 10 MB).
            </p>
            <UploadButton variant="secondary" />
          </div>
        )}

        <div className="mt-8 flex flex-col gap-3 sm:flex-row">
          <label className="relative flex-1">
            <span className="sr-only">Search documents</span>
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <input
              type="search"
              value={q}
              onChange={(e) => setFilter('q')(e.target.value)}
              placeholder="Search seller, product, number or file name"
              className="block w-full rounded-lg border border-slate-300 bg-white py-2 pr-3 pl-9 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
            />
          </label>
          <Select label="Type" value={type} onChange={setFilter('type')} options={Object.entries(DOCUMENT_TYPE_LABELS)} />
          <Select label="Status" value={status} onChange={setFilter('status')} options={STATUS_OPTIONS} />
          <Select label="From" value={source} onChange={setFilter('source')} options={SOURCE_OPTIONS} />
        </div>

        {filtered && (
          <Button variant="ghost" size="sm" icon={FilterX} onClick={() => setParams({}, { replace: true })} className="mt-3 mr-2">
            Clear filters
          </Button>
        )}
        {month && (
          // Set by a bar of the dashboard's spending chart; one click removes it.
          <button
            type="button"
            onClick={() => setFilter('month')('')}
            className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-brand-50 px-3 py-1 text-sm font-medium text-brand-700 ring-1 ring-brand-100 hover:bg-brand-100"
          >
            <CalendarDays className="size-4" aria-hidden />
            Bought in {monthLabel(month)}
            <X className="size-4" aria-label="Remove month filter" />
          </button>
        )}

        {listError && (
          <div className="mt-4">
            <Alert tone="error">{listError}</Alert>
          </div>
        )}
        {!documents && !listError && (
          <div className="mt-4 animate-pulse space-y-px overflow-hidden rounded-xl" aria-label="Loading documents">
            {[1, 2, 3, 4, 5].map((n) => (
              <div key={n} className="h-16 bg-slate-200" />
            ))}
          </div>
        )}
        {documents && !noBillsYet && (
          <DocumentList
            documents={documents.filter(
              (d) =>
                (!source || (source === 'GMAIL') === Boolean(d.sourceGmail)) &&
                (!month || (d.purchaseDate ?? '').startsWith(month)),
            )}
            filtered={filtered}
          />
        )}
      </main>
    </div>
  );
}

const SOURCE_OPTIONS: [string, string][] = [
  ['UPLOAD', 'Uploaded'],
  ['GMAIL', 'Gmail'],
];

const STATUS_OPTIONS: [DocumentStatus, string][] = [
  ['UPLOADED', 'Not read yet'],
  ['EXTRACTED', 'Needs review'],
  ['SAVED', 'Saved'],
];

function Select(props: { label: string; value: string; onChange: (value: string) => void; options: [string, string][] }) {
  return (
    <label className="flex items-center gap-2 text-sm text-slate-700">
      {props.label}
      <select
        value={props.value}
        onChange={(e) => props.onChange(e.target.value)}
        className="rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
      >
        <option value="">All</option>
        {props.options.map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
    </label>
  );
}

function DocumentList({ documents, filtered }: { documents: DocumentSummary[]; filtered: boolean }) {
  if (documents.length === 0) {
    return (
      <p className="mt-6 rounded-xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-600">
        {filtered ? 'No documents match your search.' : 'No documents yet. Upload your first bill above.'}
      </p>
    );
  }
  return (
    <ul className="mt-4 divide-y divide-slate-200 overflow-hidden rounded-xl border border-slate-200 bg-white">
      {documents.map((d) => (
        <li key={d.id}>
          <Link to={`/documents/${d.id}`} className="flex items-center gap-4 px-4 py-3 hover:bg-slate-50">
            <FileTypeIcon contentType={d.contentType} />
            <div className="min-w-0 flex-1">
              <div className="flex min-w-0 items-center gap-2">
                <p className="truncate font-medium">{d.sellerName ?? d.fileName}</p>
                <SourceBadge sourceGmail={d.sourceGmail} />
              </div>
              <p className="mt-0.5 truncate text-sm text-slate-500">
                {[
                  d.documentType && DOCUMENT_TYPE_LABELS[d.documentType],
                  d.firstProductName && (d.itemCount > 1 ? `${d.firstProductName} +${d.itemCount - 1} more` : d.firstProductName),
                  d.purchaseDate,
                  d.sellerName && d.fileName,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
            </div>
            {d.totalAmount != null && (
              <span className="shrink-0 text-sm font-medium tabular-nums">{formatAmount(d.totalAmount)}</span>
            )}
            <StatusBadge status={d.status} reading={d.readQueued} />
          </Link>
        </li>
      ))}
    </ul>
  );
}

