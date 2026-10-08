import { CircleAlert, LoaderCircle, Mail, Unplug } from 'lucide-react';
import { useFeedback } from '../../lib/feedback-context';
import { timeAgo, type GmailAccount, type ScanRange } from '../../lib/gmail';
import { Button } from '../Button';
import { ScanMenu } from './ScanMenu';

/** One connected Gmail address: its scan status, Scan and Disconnect. */
export function AccountCard(props: {
  account: GmailAccount;
  busy: boolean;
  onScan: (range: ScanRange) => void;
  onDisconnect: () => void;
}) {
  const { account } = props;
  const { confirm } = useFeedback();
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
        <Button
          variant="ghost"
          icon={Unplug}
          disabled={props.busy}
          onClick={async () => {
            const ok = await confirm({
              title: `Disconnect ${account.email}?`,
              message: 'Bill Locker stops looking in this inbox. Bills you already imported stay in Documents.',
              confirmLabel: 'Disconnect',
              danger: true,
            });
            if (ok) props.onDisconnect();
          }}
        >
          Disconnect
        </Button>
      </div>
    </div>
  );
}
