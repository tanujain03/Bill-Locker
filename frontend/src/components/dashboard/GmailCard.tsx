import { Inbox, Mail } from 'lucide-react';
import { Link } from 'react-router';
import { timeAgo, type GmailOverview } from '../../lib/gmail';
import { Panel } from './Panel';

/**
 * Gmail at a glance. It uses the Gmail page's own overview (loaded by the dashboard
 * page), so the dashboard endpoint doesn't need to know about Gmail.
 */
export function GmailCard({ gmail }: { gmail: GmailOverview | null | 'error' }) {
  const buttonClass =
    'inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700';

  let body;
  if (!gmail) {
    body = <p className="text-sm text-slate-500">Loading…</p>;
  } else if (gmail === 'error') {
    body = <p className="text-sm text-slate-500">Couldn’t load the Gmail status right now.</p>;
  } else if (!gmail.configured) {
    body = <p className="text-sm text-slate-500">Gmail import isn’t set up on this server.</p>;
  } else if (gmail.accounts.length === 0) {
    body = (
      <div className="space-y-3">
        <p className="text-sm text-slate-600">Connect Gmail and Bill Locker finds bills attached to your emails.</p>
        <Link to="/gmail" className={buttonClass}>
          <Mail className="size-4" aria-hidden />
          Connect Gmail
        </Link>
      </div>
    );
  } else {
    const scans = gmail.accounts.map((a) => a.lastScannedAt).filter((t): t is string => Boolean(t)).sort();
    const lastScan = scans.at(-1);
    body = (
      <div className="space-y-4">
        <dl className="grid grid-cols-2 gap-3">
          <div className="rounded-xl bg-slate-50 p-3">
            <dt className="text-xs text-slate-500">Accounts</dt>
            <dd className="text-xl font-semibold tabular-nums">{gmail.accounts.length}</dd>
          </div>
          <div className={`rounded-xl p-3 ${gmail.counts.toReview > 0 ? 'bg-amber-50' : 'bg-slate-50'}`}>
            <dt className="text-xs text-slate-500">Files to review</dt>
            <dd className="text-xl font-semibold tabular-nums">{gmail.counts.toReview}</dd>
          </div>
        </dl>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-xs text-slate-500">{lastScan ? `Last scan ${timeAgo(lastScan)}` : 'Not scanned yet'}</p>
          <Link to="/gmail" className={buttonClass}>
            <Inbox className="size-4" aria-hidden />
            Review files
          </Link>
        </div>
      </div>
    );
  }

  return (
    <Panel icon={Mail} title="Gmail">
      {body}
    </Panel>
  );
}
