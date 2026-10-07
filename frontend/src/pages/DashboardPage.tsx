import { CircleAlert, Package, ReceiptText, Upload, Wallet, Mail } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { ExpiringList } from '../components/dashboard/ExpiringList';
import { GmailCard } from '../components/dashboard/GmailCard';
import { RecentBills } from '../components/dashboard/RecentBills';
import { SpendingChart } from '../components/dashboard/SpendingChart';
import { StatCard } from '../components/dashboard/StatCard';
import { TopShops } from '../components/dashboard/TopShops';
import { WarrantyHealth } from '../components/dashboard/WarrantyHealth';
import { Alert } from '../components/FormParts';
import { errorMessage } from '../lib/api';
import { useAuth } from '../lib/auth-context';
import { getDashboard, type Dashboard } from '../lib/dashboard';
import { formatAmount } from '../lib/documents';
import { getGmail, type GmailOverview } from '../lib/gmail';

/**
 * /home: how your bills stand today. The backend sends every number ready-made
 * (GET /api/dashboard); every card, bar and row here is a link to the matching list.
 */
export function DashboardPage() {
  const { user } = useAuth();
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [gmail, setGmail] = useState<GmailOverview | null | 'error'>(null);
  const latest = useRef(0);

  const load = useCallback(() => {
    const mine = ++latest.current;
    setError(null);
    getDashboard()
      .then((d) => mine === latest.current && setData(d))
      .catch((e) => mine === latest.current && setError(errorMessage(e)));
    getGmail()
      .then((g) => mine === latest.current && setGmail(g))
      .catch(() => mine === latest.current && setGmail('error'));
  }, []);

  useEffect(load, [load]);

  const firstName = user?.name?.split(' ')[0] ?? '';

  return (
    <div className="min-h-dvh">
      <main className="mx-auto max-w-6xl px-4 py-8">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">
              {greeting()}
              {firstName && `, ${firstName}`}
            </h1>
            <p className="mt-1 text-slate-600">Here’s how your bills stand today.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link
              to="/gmail"
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              <Mail className="size-4" aria-hidden />
              Import from Gmail
            </Link>
            <Link
              to="/documents"
              className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-brand-700"
            >
              <Upload className="size-4" aria-hidden />
              Upload a bill
            </Link>
          </div>
        </div>

        <div className="mt-6">
          {error ? (
            <div className="space-y-3">
              <Alert tone="error">{error}</Alert>
              <button type="button" onClick={load} className="text-sm font-medium text-brand-700 hover:underline">
                Try again
              </button>
            </div>
          ) : !data ? (
            <Skeleton />
          ) : data.savedBills === 0 && data.recentBills.length === 0 ? (
            <Welcome />
          ) : (
            <Overview data={data} gmail={gmail} />
          )}
        </div>
      </main>
    </div>
  );
}

function Overview({ data, gmail }: { data: Dashboard; gmail: GmailOverview | null | 'error' }) {
  const gmailFiles = gmail && gmail !== 'error' ? gmail.counts.toReview : 0;
  const { toReview, readFailed, reading } = data.attention;
  const waiting = toReview + readFailed + reading;
  const attention = waiting + gmailFiles;
  // Only the parts that aren't zero, e.g. "2 to review · 7 in Gmail".
  const attentionParts = [
    toReview && `${toReview} to review`,
    readFailed && `${readFailed} failed`,
    reading && `${reading} reading`,
    gmailFiles && `${gmailFiles} in Gmail`,
  ].filter(Boolean);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          icon={ReceiptText}
          label="Bills saved"
          value={data.savedBills}
          sub={`${data.savedBillsThisMonth} bought this month`}
          to="/documents?status=SAVED"
        />
        <StatCard
          icon={Package}
          label="Products"
          value={data.products}
          sub={`${data.productsWithWarranty} with warranty dates`}
          to="/warranties"
        />
        <StatCard
          icon={Wallet}
          label="Total spent"
          value={formatAmount(data.totalSpent)}
          sub={`${formatAmount(data.spentThisMonth)} this month`}
          onClick={() => document.getElementById('spending')?.scrollIntoView({ behavior: 'smooth', block: 'center' })}
        />
        <StatCard
          icon={CircleAlert}
          label="Needs your attention"
          value={attention}
          sub={attention ? attentionParts.join(' · ') : 'All caught up'}
          to={attentionLink(toReview, readFailed + reading, gmailFiles)}
          attention={attention > 0}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <WarrantyHealth counts={data.warranties} />
        <ExpiringList items={data.expiringSoon} />
        <SpendingChart months={data.spendingByMonth} withoutDate={data.billsWithoutDateOrTotal} />
        <TopShops shops={data.topShops} />
        <RecentBills bills={data.recentBills} />
        <GmailCard gmail={gmail} />
      </div>
    </div>
  );
}

/**
 * Where "Needs your attention" leads: bills to review first; else bills whose AI read
 * failed or is still running (they are "Not read yet"); else the Gmail files waiting.
 * So the card never opens an empty list.
 */
function attentionLink(toReview: number, notRead: number, gmailFiles: number): string {
  if (toReview > 0) return '/documents?status=EXTRACTED';
  if (notRead > 0) return '/documents?status=UPLOADED';
  if (gmailFiles > 0) return '/gmail';
  return '/documents';
}

/** First visit: one clear next step instead of a grid of zeros. */
function Welcome() {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
      <span className="grid size-14 place-items-center rounded-2xl bg-brand-50 text-brand-600">
        <ReceiptText className="size-7" aria-hidden />
      </span>
      <h2 className="mt-4 text-lg font-semibold">Let’s add your first bill</h2>
      <p className="mt-1 max-w-md text-sm text-slate-600">
        Upload an invoice or warranty card, or connect Gmail to find bills in your inbox. We read the details for you,
        and this page fills up with your spending and warranties.
      </p>
      <div className="mt-6 flex flex-wrap justify-center gap-2">
        <Link
          to="/documents"
          className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
        >
          <Upload className="size-4" aria-hidden />
          Upload a bill
        </Link>
        <Link
          to="/gmail"
          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          <Mail className="size-4" aria-hidden />
          Connect Gmail
        </Link>
      </div>
    </div>
  );
}

/** Grey blocks in the page's shape while the numbers load. */
function Skeleton() {
  return (
    <div className="animate-pulse space-y-6" aria-label="Loading dashboard">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[1, 2, 3, 4].map((n) => (
          <div key={n} className="h-24 rounded-2xl bg-slate-200" />
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        {[1, 2, 3, 4].map((n) => (
          <div key={n} className="h-64 rounded-2xl bg-slate-200" />
        ))}
      </div>
    </div>
  );
}

function greeting(): string {
  const hour = new Date().getHours();
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
}
