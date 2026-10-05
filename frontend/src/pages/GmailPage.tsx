import { ArrowRight, Download, Inbox } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { GmailAccountCard } from '@/components/gmail/GmailAccountCard';
import { GmailConnectPanel } from '@/components/gmail/GmailConnectPanel';
import { GmailMessageRow } from '@/components/gmail/GmailMessageRow';
import { BULK_IMPORT_MIN_CONFIDENCE } from '@/components/gmail/gmail-utils';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { FilterTabs } from '@/components/ui/misc';
import { PageHeader } from '@/components/ui/PageHeader';
import { useToast } from '@/components/ui/toast-context';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useGmailConnection, useGmailMessages, useIgnoreGmailMessage, useImportGmailMessages } from '@/hooks/useGmail';
import { getErrorMessage } from '@/lib/api-client';
import type { GmailConnection, GmailMessage, GmailMessageStatus } from '@/types';
import { pluralize } from '@/utils/format';

export function GmailPage() {
  useDocumentTitle('Gmail Import');
  const connection = useGmailConnection();
  const [params, setParams] = useSearchParams();
  const toast = useToast();
  const handledRedirect = useRef<string | null>(null);

  // The backend's OAuth callback redirects here with ?status=connected|error.
  useEffect(() => {
    const status = params.get('status');
    if (!status) return;
    // React runs effects twice in development (StrictMode): show the message only once.
    if (handledRedirect.current === params.toString()) return;
    handledRedirect.current = params.toString();
    if (status === 'connected') toast.success('Gmail connected', 'We’re scanning your inbox for bills now.');
    else toast.error('Gmail was not connected', params.get('reason') ?? 'Access was denied or the request expired.');
    setParams({}, { replace: true });
    void connection.refetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- react to the redirect once
  }, [params]);

  return (
    <>
      <PageHeader
        title="Gmail Import"
        description="Find the bills, invoices and warranty cards already in your inbox — no manual uploads needed."
      />
      {connection.isPending ? (
        <Skeleton className="h-64 rounded-2xl" />
      ) : connection.isError ? (
        <Card>
          <ErrorState title="Could not load your Gmail connection" error={connection.error} onRetry={() => void connection.refetch()} />
        </Card>
      ) : connection.data.connected ? (
        <ConnectedView connection={connection.data} />
      ) : (
        <GmailConnectPanel />
      )}
    </>
  );
}

function ConnectedView({ connection }: { connection: GmailConnection }) {
  const messages = useGmailMessages(true);
  const importMessages = useImportGmailMessages();
  const ignoreMessage = useIgnoreGmailMessage();
  const toast = useToast();
  const [tab, setTab] = useState<GmailMessageStatus>('NEW');
  const [busyIds, setBusyIds] = useState<string[]>([]);

  const list = messages.data ?? [];
  const byStatus = (status: GmailMessageStatus) => list.filter((message) => message.status === status);
  const visible = byStatus(tab).sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  const bulkCandidates = byStatus('NEW').filter((message) => message.confidence >= BULK_IMPORT_MIN_CONFIDENCE);

  async function runImport(ids: string[]) {
    setBusyIds((current) => [...current, ...ids]);
    try {
      const result = await importMessages.mutateAsync(ids);
      toast.show({
        tone: 'success',
        title: `${pluralize(result.documents.length, 'bill')} sent for reading`,
        description: 'You’ll be notified when they’re ready for review.',
      });
    } catch (error) {
      toast.error('Import failed', getErrorMessage(error));
    } finally {
      setBusyIds((current) => current.filter((id) => !ids.includes(id)));
    }
  }

  async function ignore(message: GmailMessage) {
    setBusyIds((current) => [...current, message.id]);
    try {
      await ignoreMessage.mutateAsync(message.id);
    } catch (error) {
      toast.error('Could not ignore the email', getErrorMessage(error));
    } finally {
      setBusyIds((current) => current.filter((id) => id !== message.id));
    }
  }

  return (
    <div className="space-y-6">
      <GmailAccountCard connection={connection} />

      <Card>
        <CardHeader
          title="Bills found in your inbox"
          description="These emails look like bills. Import the ones you want — every bill still waits for your review."
          action={
            tab === 'NEW' &&
            bulkCandidates.length > 1 && (
              <Button
                size="sm"
                onClick={() => void runImport(bulkCandidates.map((message) => message.id))}
                loading={importMessages.isPending && busyIds.length > 1}
                leftIcon={<Download className="size-4" aria-hidden />}
              >
                Import all {bulkCandidates.length}
              </Button>
            )
          }
        />
        <div className="px-5 pt-4 sm:px-6">
          <FilterTabs
            label="Filter emails"
            value={tab}
            onChange={setTab}
            options={[
              { value: 'NEW', label: 'New', count: byStatus('NEW').length },
              { value: 'IMPORTED', label: 'Imported', count: byStatus('IMPORTED').length },
              { value: 'IGNORED', label: 'Ignored', count: byStatus('IGNORED').length },
            ]}
          />
        </div>

        <div className="mt-2">
          {messages.isPending ? (
            <div className="space-y-4 p-5 sm:p-6">
              {Array.from({ length: 3 }, (_, index) => (
                <Skeleton key={index} className="h-20 w-full" />
              ))}
            </div>
          ) : messages.isError ? (
            <ErrorState compact error={messages.error} onRetry={() => void messages.refetch()} />
          ) : visible.length === 0 ? (
            <EmptyState
              compact
              icon={<Inbox aria-hidden />}
              title={
                tab === 'NEW'
                  ? connection.syncStatus === 'SYNCING'
                    ? 'Scanning your inbox…'
                    : 'No new bills found'
                  : tab === 'IMPORTED'
                    ? 'Nothing imported yet'
                    : 'No ignored emails'
              }
              description={
                tab === 'NEW'
                  ? 'Run a scan any time — new invoices will show up here.'
                  : tab === 'IMPORTED'
                    ? 'Imported bills appear in Documents, ready for review.'
                    : 'Emails you ignore are kept here in case you change your mind.'
              }
              action={
                tab === 'IMPORTED' && (
                  <Link to="/documents" className="inline-flex items-center gap-1 text-sm font-medium text-brand-700">
                    Go to documents <ArrowRight className="size-4" aria-hidden />
                  </Link>
                )
              }
            />
          ) : (
            <ul className="divide-y divide-slate-100 border-t border-slate-100">
              {visible.map((message) => (
                <GmailMessageRow
                  key={message.id}
                  message={message}
                  busy={busyIds.includes(message.id)}
                  onImport={(target) => void runImport([target.id])}
                  onIgnore={(target) => void ignore(target)}
                />
              ))}
            </ul>
          )}
        </div>
      </Card>
    </div>
  );
}
