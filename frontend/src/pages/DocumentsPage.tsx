import { Search, Upload } from 'lucide-react';
import { useEffect, useRef, useState, type DragEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { AppHeader } from '../components/AppHeader';
import { FileTypeIcon, SourceBadge } from '../components/documents/SourceBadge';
import { StatusBadge } from '../components/documents/StatusBadge';
import { Alert } from '../components/FormParts';
import { errorMessage } from '../lib/api';
import {
  DOCUMENT_TYPE_LABELS,
  formatAmount,
  listDocuments,
  uploadDocument,
  type DocumentStatus,
  type DocumentSummary,
} from '../lib/documents';
import { usePolling } from '../lib/usePolling';

const ACCEPT = '.pdf,.jpg,.jpeg,.png,.webp';

/** /documents: upload a bill, and the list of your bills with search and filters. */
export function DocumentsPage() {
  const navigate = useNavigate();
  const fileInput = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

  const [q, setQ] = useState('');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  /** '' (all), 'UPLOAD' or 'GMAIL'. Filtered here: the list is already loaded. */
  const [source, setSource] = useState('');
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

  async function upload(file: File | undefined) {
    if (!file) return;
    setUploading(true);
    setUploadError(null);
    try {
      const document = await uploadDocument(file);
      // ?read=1 tells the document page to read it with AI straight away.
      navigate(`/documents/${document.id}?read=1`);
    } catch (error) {
      setUploadError(errorMessage(error));
      setUploading(false);
    }
  }

  function onDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    upload(event.dataTransfer.files[0]);
  }

  return (
    <div className="min-h-dvh">
      <AppHeader />
      <main className="mx-auto max-w-6xl px-4 py-8">
        <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
        <p className="mt-1 text-slate-600">Upload invoices, warranty cards and receipts. We read the details for you.</p>

        <div
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={`mt-6 flex flex-col items-center rounded-2xl border-2 border-dashed px-6 py-10 text-center ${
            dragging ? 'border-brand-500 bg-brand-50' : 'border-slate-300 bg-white'
          }`}
        >
          <span className="grid size-12 place-items-center rounded-xl bg-brand-50 text-brand-600">
            <Upload className="size-6" aria-hidden />
          </span>
          <p className="mt-3 font-medium">{uploading ? 'Uploading…' : 'Drop a bill here'}</p>
          <p className="mt-1 text-sm text-slate-500">PDF, JPG, PNG or WebP, up to 10 MB</p>
          <button
            type="button"
            disabled={uploading}
            onClick={() => fileInput.current?.click()}
            className="mt-4 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
          >
            Choose file
          </button>
          <input
            ref={fileInput}
            type="file"
            accept={ACCEPT}
            className="hidden"
            onChange={(e) => {
              upload(e.target.files?.[0]);
              e.target.value = ''; // so choosing the same file again still fires onChange
            }}
          />
        </div>
        {uploadError && (
          <div className="mt-4">
            <Alert tone="error">{uploadError}</Alert>
          </div>
        )}

        <div className="mt-8 flex flex-col gap-3 sm:flex-row">
          <label className="relative flex-1">
            <span className="sr-only">Search documents</span>
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <input
              type="search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search seller, product, number or file name"
              className="block w-full rounded-lg border border-slate-300 bg-white py-2 pr-3 pl-9 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
            />
          </label>
          <Select label="Type" value={type} onChange={setType} options={Object.entries(DOCUMENT_TYPE_LABELS)} />
          <Select label="Status" value={status} onChange={setStatus} options={STATUS_OPTIONS} />
          <Select label="From" value={source} onChange={setSource} options={SOURCE_OPTIONS} />
        </div>

        {listError && (
          <div className="mt-4">
            <Alert tone="error">{listError}</Alert>
          </div>
        )}
        {documents && (
          <DocumentList
            documents={documents.filter((d) => !source || (source === 'GMAIL') === Boolean(d.sourceGmail))}
            filtered={Boolean(q || type || status || source)}
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
              <span className="hidden text-sm font-medium tabular-nums sm:block">{formatAmount(d.totalAmount)}</span>
            )}
            <StatusBadge status={d.status} reading={d.readQueued} />
          </Link>
        </li>
      ))}
    </ul>
  );
}

