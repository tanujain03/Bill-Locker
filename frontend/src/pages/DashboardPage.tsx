import { ArrowRight, CircleAlert, Mail, Package, ReceiptText, Wallet } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { ExpiringList } from '../components/dashboard/ExpiringList';
import { GmailCard } from '../components/dashboard/GmailCard';
import { RecentBills } from '../components/dashboard/RecentBills';
import { SpendingChart } from '../components/dashboard/SpendingChart';
import { StatCard } from '../components/dashboard/StatCard';
import { TopShops } from '../components/dashboard/TopShops';
import { WarrantyHealth } from '../components/dashboard/WarrantyHealth';
import { buttonClass } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { UploadButton } from '../components/UploadButton';
import { errorMessage } from '../lib/api';
import { usePageTitle } from '../lib/usePageTitle';
import { useAuth } from '../lib/auth-context';
import { getDashboard, type Dashboard } from '../lib/dashboard';
import { formatAmount } from '../lib/documents';
import { getGmail, type GmailOverview } from '../lib/gmail';

/**
 * /home: how your bills stand today. The backend sends every number ready-made
 * (GET /api/dashboard); every card, bar and row here is a link to the matching list.
 */
export function DashboardPage() {
  usePageTitle('Dashboard');
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
            <p className="mt-1 text-slate-600">{data ? summary(data) : 'Here’s how your bills and warranties stand.'}</p>
          </div>
          {/* "Upload bill" sits in the top bar and the sidebar; Gmail gets its own shortcut here. */}
          <Link
            to="/gmail"
            className={buttonClass()}
          >
            <Mail className="size-4" aria-hidden />
            Import from Gmail
          </Link>
        </div>

        <div className="mt-6">
          {error ? (
            <ErrorState title="Could not load your dashboard" message={error} onRetry={load} />
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
      {/* Something to do comes first, as one sentence with one button. */}
      {attention > 0 && (
        <Link
          to={attentionLink(toReview, readFailed + reading, gmailFiles)}
          className="flex flex-wrap items-center gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4 text-amber-900 transition hover:bg-amber-100"
        >
          <CircleAlert className="size-5 shrink-0 text-amber-600" aria-hidden />
          <p className="min-w-0 flex-1 text-sm">
            <span className="font-semibold">{attentionHeadline(toReview, readFailed, reading, gmailFiles)}</span>
            {attentionParts.length > 1 && <span className="text-amber-800"> · {attentionParts.join(' · ')}</span>}
          </p>
          <span className="inline-flex items-center gap-1 text-sm font-semibold">
            {toReview > 0 ? 'Review now' : 'Take a look'}
            <ArrowRight className="size-4" aria-hidden />
          </span>
        </Link>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          icon={ReceiptText}
          label="Bills saved"
          value={data.savedBills}
          sub={data.savedBillsThisMonth ? `${data.savedBillsThisMonth} bought this month` : 'Nothing new this month'}
          to="/documents?status=SAVED"
        />
        <StatCard
          icon={Package}
          label="Products"
          value={data.products}
          sub={data.productsWithWarranty ? `${data.productsWithWarranty} with warranty dates` : 'No warranty dates yet'}
          to="/warranties"
        />
        <StatCard
          icon={Wallet}
          label="Total spent"
          value={formatAmount(data.totalSpent, { whole: true })}
          // A zero reads like bad news on every visit, so say it in words instead.
          sub={data.spentThisMonth ? `${formatAmount(data.spentThisMonth, { whole: true })} this month` : 'No spending this month'}
          to="/documents?status=SAVED"
        />
        <StatCard
          icon={CircleAlert}
          label="Needs attention"
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
        <UploadButton>Upload a bill</UploadButton>
        <Link
          to="/gmail"
          className={buttonClass()}
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

/** The line under the greeting, e.g. "2 warranties active · 1 ends in the next 30 days". */
function summary(d: Dashboard): string {
  if (d.savedBills === 0 && d.recentBills.length === 0) return 'Add a bill to get started.';
  const { active, expiringSoon } = d.warranties;
  const parts = [
    `${active} warrant${active === 1 ? 'y' : 'ies'} active`,
    expiringSoon ? `${expiringSoon} end${expiringSoon === 1 ? 's' : ''} in the next 30 days` : 'nothing ends this month',
  ];
  return parts.join(' · ');
}

/** The banner's bold part: the most important thing waiting. */
function attentionHeadline(toReview: number, readFailed: number, reading: number, gmailFiles: number): string {
  const bills = (n: number) => (n === 1 ? '1 bill' : `${n} bills`);
  if (toReview) return `${bills(toReview)} ${toReview === 1 ? 'needs' : 'need'} your review`;
  if (readFailed) return `${bills(readFailed)} couldn’t be read`;
  if (reading) return `${bills(reading)} being read`;
  return `${gmailFiles === 1 ? '1 new bill' : `${gmailFiles} new bills`} found in Gmail`;
}

function greeting(): string {
  const hour = new Date().getHours();
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
}
