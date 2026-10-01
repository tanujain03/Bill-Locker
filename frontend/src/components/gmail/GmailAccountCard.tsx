import { CircleAlert, Mail, RefreshCw, Unplug } from 'lucide-react';
import { useState } from 'react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Switch } from '@/components/ui/misc';
import { useToast } from '@/components/ui/toast-context';
import { useDisconnectGmail, useSyncGmail, useUpdateGmailSettings } from '@/hooks/useGmail';
import { getErrorMessage } from '@/lib/api-client';
import type { GmailConnection } from '@/types';
import { formatRelativeTime } from '@/utils/format';

export function GmailAccountCard({ connection }: { connection: GmailConnection }) {
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const sync = useSyncGmail();
  const disconnect = useDisconnectGmail();
  const updateSettings = useUpdateGmailSettings();
  const toast = useToast();
  const syncing = connection.syncStatus === 'SYNCING' || sync.isPending;

  async function onSync() {
    try {
      await sync.mutateAsync();
    } catch (error) {
      toast.error('Could not start the scan', getErrorMessage(error));
    }
  }

  async function onDisconnect() {
    try {
      await disconnect.mutateAsync();
      setConfirmDisconnect(false);
      toast.success('Gmail disconnected', 'Access has been revoked. Imported documents stay in your locker.');
    } catch (error) {
      toast.error('Could not disconnect Gmail', getErrorMessage(error));
    }
  }

  async function onToggleAutoSync(autoSync: boolean) {
    try {
      await updateSettings.mutateAsync({ autoSync });
    } catch (error) {
      toast.error('Could not update the setting', getErrorMessage(error));
    }
  }

  return (
    <>
      <Card className="p-5 sm:p-6">
        <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">
          <div className="flex min-w-0 items-center gap-4">
            <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
              <Mail className="size-6" aria-hidden />
            </span>
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <p className="truncate font-semibold text-slate-900">{connection.email}</p>
                <Badge tone="success" size="sm">
                  Connected
                </Badge>
              </div>
              <p className="mt-0.5 text-sm text-slate-500" aria-live="polite">
                {syncing
                  ? 'Scanning your inbox for bills…'
                  : connection.lastSyncedAt
                    ? `Last scanned ${formatRelativeTime(connection.lastSyncedAt)}`
                    : 'Not scanned yet'}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <Switch
                checked={connection.autoSync}
                onChange={(value) => void onToggleAutoSync(value)}
                disabled={updateSettings.isPending}
                label="Scan automatically every day"
              />
              Daily auto-scan
            </label>
            <Button
              variant="secondary"
              onClick={() => void onSync()}
              disabled={syncing}
              leftIcon={<RefreshCw className={syncing ? 'size-4 animate-spin' : 'size-4'} aria-hidden />}
            >
              {syncing ? 'Scanning…' : 'Scan now'}
            </Button>
            <Button
              variant="ghost"
              className="text-slate-500 hover:bg-rose-50 hover:text-rose-600"
              onClick={() => setConfirmDisconnect(true)}
              leftIcon={<Unplug className="size-4" aria-hidden />}
            >
              Disconnect
            </Button>
          </div>
        </div>

        {syncing && (
          <div className="mt-5 h-1 overflow-hidden rounded-full bg-brand-100" aria-hidden>
            <div className="h-full w-1/3 animate-pulse rounded-full bg-brand-500" />
          </div>
        )}

        {connection.syncStatus === 'ERROR' && connection.lastError && (
          <p className="mt-4 flex items-start gap-2 rounded-xl bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">
            <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
            {connection.lastError}
          </p>
        )}
      </Card>

      <ConfirmDialog
        open={confirmDisconnect}
        onClose={() => setConfirmDisconnect(false)}
        onConfirm={() => void onDisconnect()}
        loading={disconnect.isPending}
        title="Disconnect Gmail?"
        confirmLabel="Disconnect"
        message="Bill Locker will stop scanning your inbox and revoke its access. Documents you already imported stay in your locker."
      />
    </>
  );
}
