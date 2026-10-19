import {
  ArrowLeft,
  ArrowRight,
  CalendarClock,
  CircleAlert,
  Download,
  ExternalLink,
  FileText,
  Link2,
  ShieldCheck,
  Wallet,
  Wrench,
  type LucideIcon,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { Button, buttonClass } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { ReturnBanner } from '../components/documents/ReturnWindow';
import { FileTypeIcon, SourceBadge } from '../components/documents/SourceBadge';
import { WARRANTY_STYLE, WarrantyStatusPill } from '../components/warranties/WarrantyStatusPill';
import { errorMessage } from '../lib/api';
import { claimKitLink } from '../lib/claims';
import { formatDate } from '../lib/document-form';
import { BILL_CATEGORY_LABELS, DOCUMENT_TYPE_LABELS, downloadDocument, formatAmount, type DocumentSummary } from '../lib/documents';
import { categoryIcon, daysBetween, today } from '../lib/expenses';
import { useFeedback } from '../lib/feedback-context';
import { getProduct, setWarrantyRegistration, ownedFor, type ProductView } from '../lib/products';
import { ServiceHistorySection } from '../components/services/ServiceHistorySection';
import { usePageTitle } from '../lib/usePageTitle';

/**
 * /products/:productId — one product with everything about it: its warranty on a
 * timeline, its details, what owning it has cost so far, the bill it came from and other
 * documents with the same serial or model number. Actions: claim kit, register, open bill.
 */
export function ProductPage() {
  const { productId = '' } = useParams();
  const [product, setProduct] = useState<ProductView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const latest = useRef(0);
  usePageTitle(product?.productName ?? 'Product');

  const load = useCallback(() => {
    const request = ++latest.current;
    setError(null);
    setProduct(null);
    getProduct(productId)
      .then((value) => { if (request === latest.current) setProduct(value); })
      .catch((e) => { if (request === latest.current) setError(errorMessage(e)); });
  }, [productId]);

  useEffect(() => {
    load();
    return () => { ++latest.current; };
  }, [load]);

  return (
    <main className="mx-auto max-w-6xl px-4 py-8">
      <Link to="/warranties" className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-800">
        <ArrowLeft className="size-4" aria-hidden />
        Warranties
      </Link>
      {error ? (
        <div className="mt-4">
          <ErrorState title="Could not load this product" message={error} onRetry={load} />
        </div>
      ) : !product ? (
        <div className="mt-4 animate-pulse space-y-4" aria-label="Loading product">
          <div className="h-28 rounded-2xl bg-slate-200" />
          <div className="grid gap-4 lg:grid-cols-2">
            <div className="h-56 rounded-2xl bg-slate-200" />
            <div className="h-56 rounded-2xl bg-slate-200" />
          </div>
        </div>
      ) : (
        <Product key={product.productId} p={product} onUpdate={(next) => setProduct((current) => current?.productId === next.productId ? next : current)} />
      )}
    </main>
  );
}

function Product({ p, onUpdate }: { p: ProductView; onUpdate: (p: ProductView) => void }) {
  const { toast } = useFeedback();
  const [savingRegistration, setSavingRegistration] = useState(false);
  async function changeRegistration() {
    if (savingRegistration) return;
    setSavingRegistration(true);
    try {
      onUpdate(await setWarrantyRegistration(p.productId, !p.warrantyRegistered));
      toast(p.warrantyRegistered ? 'Registration status cleared.' : 'Warranty marked as registered.');
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setSavingRegistration(false);
    }
  }
  const Icon = categoryIcon(p.category);
  const canRegister = Boolean(p.registrationUrl && /^https?:\/\//i.test(p.registrationUrl));

  return (
    <div className="mt-4 space-y-6">
      {/* Who it is, and what to do with it. */}
      <header className="flex flex-wrap items-start justify-between gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
        <div className="flex min-w-0 items-start gap-4">
          <span className="grid size-14 shrink-0 place-items-center rounded-2xl bg-brand-50 text-brand-600">
            <Icon className="size-7" aria-hidden />
          </span>
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold tracking-tight">{p.productName ?? 'Unnamed product'}</h1>
            <p className="mt-0.5 text-slate-600">
              {[p.brand, p.modelNumber && `Model ${p.modelNumber}`, p.category && BILL_CATEGORY_LABELS[p.category]]
                .filter(Boolean)
                .join(' · ') || 'No brand or model yet'}
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <WarrantyStatusPill status={p.status} daysLeft={p.daysLeft} />
              {p.price !== null && <span className="text-sm font-semibold tabular-nums">{formatAmount(p.price)}</span>}
            </div>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to={claimKitLink(p.productId)} className={buttonClass({ variant: 'primary' })}>
            <Wrench className="size-4" aria-hidden />
            Claim kit
          </Link>
          {p.warrantyRegistered ? (
            <Button icon={ShieldCheck} disabled>Warranty registered</Button>
          ) : canRegister && (
            <a href={p.registrationUrl!} target="_blank" rel="noopener noreferrer" className={buttonClass()}>
              <ExternalLink className="size-4" aria-hidden />
              Register warranty
            </a>
          )}
          <Link to={`/documents/${p.documentId}`} className={buttonClass()}>
            <FileText className="size-4" aria-hidden />
            Open bill
          </Link>
        </div>
      </header>

      <ServiceHistorySection product={p} onChanged={() => { getProduct(p.productId).then(onUpdate).catch((e) => toast(errorMessage(e), 'error')); }} />

      {p.bill.returnByDate && <ReturnBanner returnByDate={p.bill.returnByDate} />}

      {p.missing.length > 0 && (
        <Link
          to={`/documents/${p.documentId}`}
          className="flex items-center justify-between gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-5 py-3 text-sm text-amber-900 hover:bg-amber-100"
        >
          <span className="flex items-center gap-2">
            <CircleAlert className="size-4 shrink-0" aria-hidden />
            Missing: {p.missing.join(', ')}. Add {p.missing.length === 1 ? 'it' : 'them'} on the bill, so a claim has every detail.
          </span>
          <ArrowRight className="size-4 shrink-0" aria-hidden />
        </Link>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card icon={ShieldCheck} title="Warranty">
          <WarrantyTimeline p={p} />
          <div className="mt-4 border-t border-slate-100 pt-4">
            <p className="mb-2 text-sm text-slate-600">{p.warrantyRegistered ? 'You marked this warranty as registered.' : 'After completing registration with the brand, record it here.'}</p>
            <Button size="sm" busy={savingRegistration} onClick={changeRegistration}>
              {p.warrantyRegistered ? 'Undo registered status' : 'Mark as registered'}
            </Button>
          </div>
        </Card>

        <Card icon={Wallet} title="Owning it">
          {p.ownedDays === null ? (
            <p className="text-sm text-slate-600">Add the purchase date on the bill to see how long you’ve owned it.</p>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              <Stat label="Owned for" value={ownedFor(p.ownedDays)} sub={p.purchaseDate ? `since ${formatDate(p.purchaseDate)}` : undefined} />
              <Stat
                label="Cost per month"
                value={p.costPerMonth !== null ? formatAmount(p.costPerMonth, { whole: true }) : '—'}
                sub={p.price !== null ? `${formatAmount(p.price, { whole: true })} plus service costs, spread over the months owned` : 'Add the price on the bill'}
              />
            </div>
          )}
        </Card>

        <Card icon={FileText} title="Details">
          <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Fact label="Brand" value={p.brand} />
            <Fact label="Model number" value={p.modelNumber} />
            <Fact label="Serial number" value={p.serialNumber} />
            <Fact label="Price" value={p.price !== null ? formatAmount(p.price) : null} />
            <Fact label="Bought at" value={p.sellerName} />
            <Fact label="Purchase date" value={p.purchaseDate && formatDate(p.purchaseDate)} />
            <Fact label="Invoice number" value={p.invoiceNumber} />
            <Fact label="Warranty by" value={p.warrantyProvider} />
          </dl>
        </Card>

        <Card icon={Link2} title="Documents">
          <ul className="-mx-2 divide-y divide-slate-100">
            <DocumentRow doc={p.bill} note="The bill this product is on" />
            {p.related.map((r) => (
              <DocumentRow key={r.document.id} doc={r.document} note={`Same ${r.matchedBy}`} />
            ))}
          </ul>
          {p.related.length === 0 && (
            <p className="mt-3 text-xs text-slate-500">
              {p.serialNumber || p.modelNumber
                ? 'A warranty card or service bill with the same serial or model number will show up here.'
                : 'Add the serial or model number on the bill, and documents that mention it will show up here.'}
            </p>
          )}
        </Card>
      </div>
    </div>
  );
}

/**
 * The warranty as a bar from its start to its end, with today marked. The colour follows the
 * status, and the words under it say the same (never colour alone).
 */
function WarrantyTimeline({ p }: { p: ProductView }) {
  const start = p.warrantyStartDate ?? p.purchaseDate;
  if (!p.warrantyEndDate || !start) {
    return (
      <p className="text-sm text-slate-600">
        The bill doesn’t say how long the warranty lasts. Add the warranty months or end date on the bill to see it here.
      </p>
    );
  }
  const total = daysBetween(start, p.warrantyEndDate);
  const gone = daysBetween(start, today()) - 1;
  const used = Math.min(Math.max(gone / Math.max(total, 1), 0), 1);
  const { bar } = WARRANTY_STYLE[p.status];
  // "a 12-month warranty" (a number before a noun takes no plural)
  const length = p.warrantyMonths ? `${p.warrantyMonths}-month` : `${total}-day`;

  return (
    <div>
      <p className="text-sm text-slate-700">
        {p.status === 'EXPIRED' ? (
          <>The {length} warranty ended on <strong>{formatDate(p.warrantyEndDate)}</strong>.</>
        ) : (
          <>
            <strong>{p.daysLeft === 1 ? '1 day' : `${p.daysLeft} days`}</strong> left of a {length} warranty
            {p.warrantyProvider ? ` from ${p.warrantyProvider}` : ''}.
          </>
        )}
      </p>
      <div
        className="relative mt-5 h-3 rounded-full bg-slate-100"
        role="meter"
        aria-label="Warranty used"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(used * 100)}
      >
        <span className={`absolute inset-y-0 left-0 rounded-full ${bar}`} style={{ width: `${used * 100}%` }} />
        {/* Today */}
        {p.status !== 'EXPIRED' && (
          <span className="absolute -top-1.5 h-6 w-0.5 rounded bg-slate-900" style={{ left: `calc(${used * 100}% - 1px)` }} aria-hidden />
        )}
      </div>
      <div className="mt-2 flex justify-between text-xs text-slate-500">
        <span>Started {formatDate(start)}</span>
        <span>Ends {formatDate(p.warrantyEndDate)}</span>
      </div>
      {p.status !== 'EXPIRED' && (
        <p className="mt-4 flex items-center gap-1.5 text-xs text-slate-500">
          <CalendarClock className="size-3.5" aria-hidden />
          Bill Locker reminds you 30 days before it ends.
        </p>
      )}
    </div>
  );
}

function DocumentRow({ doc, note }: { doc: DocumentSummary; note: string }) {
  const { toast } = useFeedback();
  const [busy, setBusy] = useState(false);

  async function download() {
    setBusy(true);
    try {
      const url = URL.createObjectURL(await downloadDocument(doc.id));
      Object.assign(document.createElement('a'), { href: url, download: doc.fileName }).click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex items-center gap-3 px-2 py-2.5">
      <FileTypeIcon contentType={doc.contentType} size="sm" />
      <Link to={`/documents/${doc.id}`} className="min-w-0 flex-1 hover:underline">
        <span className="block truncate text-sm font-medium">{doc.sellerName ?? doc.fileName}</span>
        <span className="block truncate text-xs text-slate-500">
          {[note, doc.documentType && DOCUMENT_TYPE_LABELS[doc.documentType], doc.purchaseDate && formatDate(doc.purchaseDate)]
            .filter(Boolean)
            .join(' · ')}
        </span>
      </Link>
      <SourceBadge sourceGmail={doc.sourceGmail} sample={doc.sample} />
      <Button variant="ghost" size="sm" icon={Download} busy={busy} onClick={download} aria-label={`Download ${doc.fileName}`} title="Download" />
    </li>
  );
}

function Card(props: { icon: LucideIcon; title: string; children: ReactNode }) {
  const Icon = props.icon;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
      <h2 className="flex items-center gap-2 font-semibold">
        <Icon className="size-4.5 text-brand-600" aria-hidden />
        {props.title}
      </h2>
      <div className="mt-4">{props.children}</div>
    </section>
  );
}

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-0.5 text-xl font-semibold tabular-nums">{value}</p>
      {sub && <p className="mt-0.5 text-xs text-slate-500">{sub}</p>}
    </div>
  );
}

/** One detail; a missing one says so in amber, so it stands out. */
function Fact({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className={value ? 'font-medium break-words' : 'text-amber-700'}>{value ?? 'Not on the bill'}</dd>
    </div>
  );
}
