import {
  ArrowLeft,
  CircleAlert,
  CircleCheck,
  Download,
  ExternalLink,
  FileText,
  Mail,
  MapPin,
  Phone,
  Printer,
  Search,
  ShieldCheck,
  Wrench,
  type LucideIcon,
} from 'lucide-react';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { Button, buttonClass } from '../components/Button';
import { CopyButton } from '../components/documents/CopyButton';
import { ErrorState } from '../components/ErrorState';
import { WarrantyStatusPill } from '../components/warranties/WarrantyStatusPill';
import { errorMessage } from '../lib/api';
import { useAuth } from '../lib/auth-context';
import { claimEmail, COMMON_PROBLEMS, getClaimKit, mailtoLink, type ClaimKit } from '../lib/claims';
import { formatDate } from '../lib/document-form';
import { downloadDocument, formatAmount } from '../lib/documents';
import { today } from '../lib/expenses';
import { useFeedback } from '../lib/feedback-context';
import { usePageTitle } from '../lib/usePageTitle';

/**
 * /warranties/claim/:documentId/:position — everything needed to claim one product's
 * warranty: its status, the facts, a ready claim email, where to send it and what is
 * missing. The app never sends anything: the user copies the email or opens it in their
 * own email app, and can print the page as a claim sheet.
 */
export function ClaimKitPage() {
  usePageTitle('Warranty claim kit');
  const { documentId = '', position = '0' } = useParams();
  const [kit, setKit] = useState<ClaimKit | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    getClaimKit(documentId, Number(position))
      .then(setKit)
      .catch((e) => setError(errorMessage(e)));
  }, [documentId, position]);

  useEffect(load, [load]);

  return (
    <main className="mx-auto max-w-6xl px-4 py-8 print:max-w-none print:p-0">
      <Link to="/warranties" className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-800 print:hidden">
        <ArrowLeft className="size-4" aria-hidden />
        Warranties
      </Link>
      {error ? (
        <div className="mt-4">
          <ErrorState title="Could not load the claim kit" message={error} onRetry={load} />
        </div>
      ) : !kit ? (
        <div className="mt-4 animate-pulse space-y-4" aria-label="Loading claim kit">
          <div className="h-28 rounded-2xl bg-slate-200" />
          <div className="h-80 rounded-2xl bg-slate-200" />
        </div>
      ) : (
        <Kit kit={kit} />
      )}
    </main>
  );
}

function Kit({ kit }: { kit: ClaimKit }) {
  const { user } = useAuth();
  const { toast } = useFeedback();
  const [problem, setProblem] = useState('');
  const [since, setSince] = useState('');
  const [downloading, setDownloading] = useState(false);
  // Signed with the signed-in user's name and email (Settings → Profile), not the buyer printed on the bill.
  const email = claimEmail(kit, { problem, since, name: user?.name ?? '', email: user?.email ?? '' });
  const product = kit.productName ?? 'Unnamed product';

  async function downloadInvoice() {
    setDownloading(true);
    try {
      const url = URL.createObjectURL(await downloadDocument(kit.documentId));
      Object.assign(document.createElement('a'), { href: url, download: kit.fileName }).click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="mt-4 space-y-6">
      {/* Title and how the warranty stands today. */}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-brand-700">Warranty claim kit</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">{product}</h1>
          <p className="mt-1 text-slate-600">
            {[kit.brand, kit.modelNumber && `Model ${kit.modelNumber}`, kit.sellerName && `bought at ${kit.sellerName}`]
              .filter(Boolean)
              .join(' · ')}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 print:hidden">
          <Button icon={Download} busy={downloading} onClick={downloadInvoice}>
            Download invoice
          </Button>
          <Button icon={Printer} onClick={() => window.print()}>
            Print claim sheet
          </Button>
        </div>
      </header>

      <StatusBanner kit={kit} />

      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-6">
          {/* 1. The problem */}
          <Card step={1} title="Describe the problem" className="print:hidden">
            <textarea
              value={problem}
              onChange={(e) => setProblem(e.target.value)}
              rows={3}
              placeholder="What happened? For example: the phone does not charge since last week."
              aria-label="Describe the problem"
              className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
            />
            <div className="mt-2 flex flex-wrap gap-1.5" role="group" aria-label="Common problems">
              {COMMON_PROBLEMS.map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setProblem(p)}
                  className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-200"
                >
                  {p.replace(/\.$/, '')}
                </button>
              ))}
            </div>
            <label className="mt-4 flex flex-wrap items-center gap-2 text-sm text-slate-700">
              Started on
              <input
                type="date"
                value={since}
                max={today()}
                onChange={(e) => setSince(e.target.value)}
                className="rounded-lg border border-slate-300 px-2 py-1.5 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
              />
              <span className="text-xs text-slate-500">(optional)</span>
            </label>
          </Card>

          {/* 2. The email */}
          <Card step={2} title="Your claim email">
            <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Subject</p>
            <p className="mt-1 text-sm font-medium">{email.subject}</p>
            <p className="mt-4 text-xs font-medium tracking-wide text-slate-500 uppercase">Message</p>
            {/* Plain text in a <pre>: nothing from the bill is ever rendered as HTML. */}
            <pre className="mt-1 max-h-96 overflow-auto rounded-xl bg-slate-50 p-4 font-sans text-sm whitespace-pre-wrap text-slate-800 ring-1 ring-slate-200 print:max-h-none print:overflow-visible">
              {email.body}
            </pre>
            <div className="mt-4 flex flex-wrap gap-2 print:hidden">
              <a href={mailtoLink(email.subject, email.body)} className={buttonClass({ variant: 'primary' })}>
                <Mail className="size-4" aria-hidden />
                Open in email app
              </a>
              <CopyButton text={`Subject: ${email.subject}\n\n${email.body}`} label="Copy the email">
                Copy email
              </CopyButton>
            </div>
            <p className="mt-3 text-xs text-slate-500 print:hidden">
              Your email app opens with this draft. Add the brand’s support address, attach the invoice
              (Download invoice above), and send it yourself. Bill Locker never sends anything for you.
            </p>
          </Card>
        </div>

        <div className="space-y-6">
          {/* 3. Where to go */}
          <Card step={3} title="Where to send it">
            <ul className="space-y-1">
              <ActionLink href={kit.supportSearchUrl} icon={Search} title={`${kit.brand ?? 'Brand'} customer care`} sub="Find the brand’s support page and email" />
              <ActionLink href={kit.serviceCentreUrl} icon={MapPin} title="Service centres near me" sub="Opens Google Maps" />
              {kit.registrationUrl && /^https?:\/\//i.test(kit.registrationUrl) && (
                <ActionLink href={kit.registrationUrl} icon={ShieldCheck} title="Brand’s warranty page" sub="Saved with this product" />
              )}
              {kit.sellerContact && (
                <li className="flex items-center gap-3 rounded-xl px-2 py-2.5">
                  <IconBox icon={Phone} />
                  <span className="min-w-0">
                    <span className="block text-sm font-medium">Shop: {kit.sellerName ?? 'seller'}</span>
                    <span className="block truncate text-xs text-slate-500">{kit.sellerContact}</span>
                  </span>
                </li>
              )}
            </ul>
          </Card>

          {/* 4. The checklist */}
          <Card step={4} title="Claim checklist">
            <ul className="space-y-2 text-sm">
              <Check ok label="Purchase invoice" detail={kit.fileName} />
              <Fact kit={kit} label="Product name" value={kit.productName} />
              <Fact kit={kit} label="Model number" value={kit.modelNumber} />
              <Fact kit={kit} label="Serial number" value={kit.serialNumber} />
              <Fact kit={kit} label="Purchase date" value={kit.purchaseDate && formatDate(kit.purchaseDate)} />
              <Fact kit={kit} label="Invoice number" value={kit.invoiceNumber} />
              <Fact kit={kit} label="Warranty end date" value={kit.warrantyEndDate && formatDate(kit.warrantyEndDate)} />
              {kit.unitPrice !== null && <Check ok label="Price" detail={formatAmount(kit.unitPrice)} />}
            </ul>
            {kit.missing.length > 0 && (
              <Link
                to={`/documents/${kit.documentId}`}
                className="mt-4 flex items-center justify-between gap-3 rounded-xl bg-amber-50 px-3 py-2.5 text-sm text-amber-900 hover:bg-amber-100 print:hidden"
              >
                <span>
                  {kit.missing.length === 1 ? '1 detail is' : `${kit.missing.length} details are`} missing. Service centres
                  usually ask for {kit.missing.length === 1 ? 'it' : 'them'}: add {kit.missing.length === 1 ? 'it' : 'them'} on the bill.
                </span>
                <FileText className="size-4 shrink-0" aria-hidden />
              </Link>
            )}
            <p className="mt-4 text-xs text-slate-500">
              Also keep the product, its box and any accessories ready; service centres often ask to see them.
            </p>
          </Card>
        </div>
      </div>
    </div>
  );
}

/** The warranty's state in one sentence, with icon and colour (never colour alone). */
function StatusBanner({ kit }: { kit: ClaimKit }) {
  const end = kit.warrantyEndDate && formatDate(kit.warrantyEndDate);
  const [tone, Icon, text]: [string, LucideIcon, ReactNode] =
    kit.status === 'EXPIRED'
      ? ['border-rose-200 bg-rose-50 text-rose-900', CircleAlert, <>The warranty ended on <strong>{end}</strong>. You can still ask for a paid repair; some brands also help shortly after the end, so it is worth asking.</>]
      : kit.status === 'NO_INFO'
        ? ['border-slate-200 bg-slate-50 text-slate-800', CircleAlert, <>The bill doesn’t say when the warranty ends. Check the warranty card or add the end date on the bill before you claim.</>]
        : kit.status === 'EXPIRING_SOON'
          ? ['border-amber-200 bg-amber-50 text-amber-900', Wrench, <>The warranty ends on <strong>{end}</strong>. Claim soon: the request must reach the brand before that date.</>]
          : ['border-emerald-200 bg-emerald-50 text-emerald-900', ShieldCheck, <>Covered until <strong>{end}</strong>{kit.warrantyProvider ? ` by ${kit.warrantyProvider}` : ''}. A repair or replacement should be free under the warranty terms.</>];
  return (
    <div className={`flex flex-wrap items-center gap-3 rounded-2xl border px-5 py-4 ${tone}`}>
      <Icon className="size-5 shrink-0" aria-hidden />
      <p className="min-w-0 flex-1 text-sm">{text}</p>
      <WarrantyStatusPill status={kit.status} daysLeft={kit.daysLeft} />
    </div>
  );
}

function Card(props: { step: number; title: string; className?: string; children: ReactNode }) {
  return (
    <section className={`rounded-2xl border border-slate-200 bg-white p-5 shadow-xs print:break-inside-avoid print:shadow-none ${props.className ?? ''}`}>
      <h2 className="flex items-center gap-2 font-semibold">
        <span className="grid size-6 place-items-center rounded-full bg-brand-600 text-xs font-semibold text-white print:hidden">
          {props.step}
        </span>
        {props.title}
      </h2>
      <div className="mt-4">{props.children}</div>
    </section>
  );
}

function IconBox({ icon: Icon }: { icon: LucideIcon }) {
  return (
    <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-600">
      <Icon className="size-4" aria-hidden />
    </span>
  );
}

function ActionLink(props: { href: string; icon: LucideIcon; title: string; sub: string }) {
  return (
    <li>
      <a href={props.href} target="_blank" rel="noopener noreferrer" className="flex items-center gap-3 rounded-xl px-2 py-2.5 hover:bg-slate-50">
        <IconBox icon={props.icon} />
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-medium">{props.title}</span>
          <span className="block truncate text-xs text-slate-500">{props.sub}</span>
        </span>
        <ExternalLink className="size-4 shrink-0 text-slate-400" aria-hidden />
      </a>
    </li>
  );
}

/** One checklist line: a tick and the value, or a warning that it is missing. */
function Check({ ok, label, detail }: { ok: boolean; label: string; detail?: string | null }) {
  return (
    <li className="flex items-start gap-2">
      {ok ? (
        <CircleCheck className="mt-0.5 size-4 shrink-0 text-emerald-600" aria-hidden />
      ) : (
        <CircleAlert className="mt-0.5 size-4 shrink-0 text-amber-600" aria-hidden />
      )}
      <span className="min-w-0">
        <span className={ok ? 'text-slate-700' : 'font-medium text-amber-900'}>{label}</span>
        <span className="block truncate text-xs text-slate-500">{ok ? detail : 'Missing'}</span>
      </span>
    </li>
  );
}

function Fact({ kit, label, value }: { kit: ClaimKit; label: string; value: string | null }) {
  return <Check ok={!kit.missing.includes(label)} label={label} detail={value} />;
}
