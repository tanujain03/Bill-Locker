import { CircleAlert, LoaderCircle, Mail, Unplug } from 'lucide-react';
import { timeAgo, type GmailAccount, type ScanRange } from '../../lib/gmail';
import { ScanMenu } from './ScanMenu';

/** One connected Gmail address: its scan status, Scan and Disconnect. */
export function AccountCard(props: {
  account: GmailAccount;
  busy: boolean;
  onScan: (range: ScanRange) => void;
  onDisconnect: () => void;
}) {
  const { account } = props;
  const scanning = account.scanStatus === 'QUEUED' || account.scanStatus === 'SCANNING';

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 items-center gap-3">
        <Mail className="size-5 shrink-0 text-slate-400" aria-hidden />
        <div className="min-w-0">
          <p className="truncate font-medium">{account.email}</p>
          <div role="status" className="flex items-center gap-1.5 text-sm text-slate-600">
            {scanning ? (
              <>
                <LoaderCircle className="size-4 shrink-0 animate-spin text-brand-600" aria-hidden />
                Scanning…
              </>
            ) : account.scanStatus === 'ERROR' ? (
              <span className="flex items-start gap-1.5 text-rose-700">
                <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
                {account.lastError ?? 'The last scan failed.'}
              </span>
            ) : account.lastScannedAt ? (
              `Last scanned ${timeAgo(account.lastScannedAt)}`
            ) : (
              'Not scanned yet'
            )}
          </div>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <ScanMenu disabled={scanning || props.busy} onScan={props.onScan} />
        <button
          type="button"
          disabled={props.busy}
          onClick={() => {
            if (window.confirm(`Disconnect ${account.email}? Bills you imported stay in Documents.`)) props.onDisconnect();
          }}
          className="inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-60"
        >
          <Unplug className="size-4" aria-hidden />
          Disconnect
        </button>
      </div>
    </div>
  );
}
