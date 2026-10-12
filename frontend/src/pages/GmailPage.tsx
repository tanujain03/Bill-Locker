import { Info, Mail, Plus } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Button } from '../components/Button';
import { Alert } from '../components/FormParts';
import { Tab, TabList } from '../components/Tabs';
import { AccountCard } from '../components/gmail/AccountCard';
import { EmailCard } from '../components/gmail/EmailCard';
import { errorMessage } from '../lib/api';
import {
  CALLBACK_ERRORS,
  connectGmail,
  disconnectAccount,
  getGmail,
  ignoreFiles,
  importFiles,
  listEmails,
  restoreFiles,
  scanAccount,
  type EmailView,
  type GmailEmail,
  type GmailOverview,
  type ScanRange,
} from '../lib/gmail';
import { useFeedback } from '../lib/feedback-context';
import { usePolling } from '../lib/usePolling';
import { usePageTitle } from '../lib/usePageTitle';

const TABS: [EmailView, string, keyof GmailOverview['counts']][] = [
  ['TO_REVIEW', 'To review', 'toReview'],
  ['IGNORED', 'Ignored', 'ignored'],
  ['IMPORTED', 'Imported', 'imported'],
];

/** /gmail: connect Gmail accounts, scan them, and choose which found bills to import. */
export function GmailPage() {
  usePageTitle('Gmail');
  const { toast } = useFeedback();
  const [params, setParams] = useSearchParams();
  const [overview, setOverview] = useState<GmailOverview | null>(null);
  const [view, setView] = useState<EmailView>('TO_REVIEW');
  const [emails, setEmails] = useState<GmailEmail[] | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Read the redirect's result once, then drop it from the URL so a reload doesn't repeat it.
  const [notice] = useState(() => {
    const connected = params.get('connected');
    const code = params.get('error');
    if (connected) return { tone: 'success' as const, text: `Connected ${connected}. Scanning the last year…` };
    if (code) return { tone: 'error' as const, text: CALLBACK_ERRORS[code] ?? CALLBACK_ERRORS.failed };
    return null;
  });
  useEffect(() => {
    if (params.size > 0) setParams({}, { replace: true });
  }, [params, setParams]);

  // Overview (accounts + counts) and the current tab, together. Only the newest request may
  // write state, so a slow answer for a previous tab (or poll) can't overwrite the current one.
  const latest = useRef(0);
  const reload = useCallback(async () => {
    const mine = ++latest.current;
    try {
      const [o, list] = await Promise.all([getGmail(), listEmails(view)]);
      if (mine !== latest.current) return;
      setOverview(o);
      setEmails(list);
      // Drop ticks for files that are gone or no longer actionable, so the bar's count is honest.
      const actionable = new Set(
        list
          .flatMap((e) => e.files)
          .filter((f) => f.status === 'NEW' || f.status === 'FAILED' || f.status === 'IGNORED')
          .map((f) => f.id),
      );
      setSelected((s) => (s.size === 0 ? s : new Set([...s].filter((id) => actionable.has(id)))));
      setError(null);
    } catch (e) {
      if (mine === latest.current) setError(errorMessage(e));
    }
  }, [view]);

  useEffect(() => {
    setEmails(null); // don't show another tab's emails while this one loads
    setSelected(new Set());
    reload();
  }, [reload]);

  const files = emails?.flatMap((e) => e.files) ?? [];
  usePolling(
    Boolean(
      overview?.accounts.some((a) => a.scanStatus === 'QUEUED' || a.scanStatus === 'SCANNING') ||
        files.some((f) => f.status === 'IMPORTING' || f.document?.readQueued),
    ),
    reload,
  );

  /** Runs an action, refreshes everything, clears the ticks, then says what happened (a toast). */
  async function act(action: () => Promise<unknown>, done?: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
      setSelected(new Set());
      await reload();
      if (done) toast(done);
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setBusy(false);
    }
  }

  async function connect() {
    setBusy(true);
    setError(null);
    try {
      const { authorizationUrl } = await connectGmail();
      window.location.assign(authorizationUrl);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  function toggle(id: string) {
    setSelected((s) => {
      const next = new Set(s);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }

  const ids = [...selected];
  const run = (fn: (ids: string[]) => Promise<unknown>, done: (files: string) => string) => (fileIds: string[]) =>
    act(() => fn(fileIds), done(fileIds.length === 1 ? '1 file' : `${fileIds.length} files`));
  const doImport = run(importFiles, (files) => `Importing ${files}. They’ll show up in Documents.`);
  const doIgnore = run(ignoreFiles, (files) => `${files} ignored.`);
  const doRestore = run(restoreFiles, (files) => `${files} moved back to To review.`);

  return (
    <div className="min-h-dvh">
      <main className="mx-auto max-w-6xl px-4 py-8">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Gmail import</h1>
            <p className="mt-1 text-slate-600">
              Bill Locker looks for bills attached to your emails. Read-only: it never sends, changes or deletes mail.
            </p>
          </div>
          {overview?.configured && overview.accounts.length > 0 && (
            <Button variant="secondary" icon={Plus} disabled={busy} onClick={connect}>
              Connect another
            </Button>
          )}
        </div>

        <div className="mt-4 space-y-3">
          {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
          {error && <Alert tone="error">{error}</Alert>}
        </div>

        {overview && !overview.configured && (
          <div className="mt-6 flex gap-3 rounded-xl border border-slate-200 bg-white p-5 text-sm text-slate-700">
            <Info className="mt-0.5 size-5 shrink-0 text-brand-600" aria-hidden />
            {/* How to switch it on (GOOGLE_* keys in backend/.env) is in docs/task-3-gmail.md, not on screen. */}
            <p>Gmail import isn’t available right now. You can still upload bills yourself.</p>
          </div>
        )}

        {overview?.configured && (
          <>
            {overview.accounts.length === 0 ? (
              <div className="mt-6 flex flex-col items-center rounded-2xl border-2 border-dashed border-slate-300 bg-white px-6 py-10 text-center">
                <span className="grid size-12 place-items-center rounded-xl bg-brand-50 text-brand-600">
                  <Mail className="size-6" aria-hidden />
                </span>
                <p className="mt-3 font-medium">Connect Gmail to find bills in your inbox</p>
                <p className="mt-2 max-w-xl text-sm text-slate-600">
                  Bill Locker reads the sender, subject, a short preview and the names of attached files. Files are
                  downloaded only when you click Import. Access is read-only: it never sends, changes or deletes mail.
                </p>
                <Button variant="primary" icon={Plus} disabled={busy} onClick={connect} className="mt-5">
                  Connect Gmail
                </Button>
              </div>
            ) : (
              <div className="mt-6 space-y-3">
                {overview.accounts.map((a) => (
                  <AccountCard
                    key={a.id}
                    account={a}
                    busy={busy}
                    onScan={(range: ScanRange) => act(() => scanAccount(a.id, range))}
                    onDisconnect={() => act(() => disconnectAccount(a.id), `Disconnected ${a.email}.`)}
                  />
                ))}
              </div>
            )}

            {overview.accounts.length > 0 && (
              <>
                <div className="mt-8">
                  <TabList label="Found files">
                    {TABS.map(([value, label, count]) => (
                      <Tab
                        key={value}
                        label={label}
                        count={overview.counts[count]}
                        active={view === value}
                        onClick={() => setView(value)}
                      />
                    ))}
                  </TabList>
                </div>

                {emails && emails.length === 0 && (
                  <p className="mt-4 rounded-xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-600">
                    {view === 'TO_REVIEW'
                      ? 'All caught up. Run a scan to look for new bills.'
                      : view === 'IGNORED'
                        ? 'Nothing ignored.'
                        : 'Nothing imported yet.'}
                  </p>
                )}
                {emails && emails.length > 0 && (
                  <ul className="mt-4 space-y-3">
                    {emails.map((email) => (
                      <EmailCard
                        key={email.id}
                        email={email}
                        selected={selected}
                        busy={busy}
                        onToggle={toggle}
                        onImport={doImport}
                        onIgnore={doIgnore}
                        onRestore={doRestore}
                      />
                    ))}
                  </ul>
                )}
              </>
            )}
          </>
        )}

        {ids.length > 0 && (
          <div className="sticky bottom-16 z-10 -mx-1 mt-6 px-1 pb-4 sm:bottom-0">
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white/95 px-4 py-3 shadow-lg backdrop-blur">
              <p role="status" className="text-sm font-medium text-slate-700">
                {ids.length} selected
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <Button variant="primary" disabled={busy} onClick={() => doImport(ids)}>
                  Import selected{view === 'TO_REVIEW' ? ` (${ids.length})` : ''}
                </Button>
                <Button disabled={busy} onClick={() => (view === 'IGNORED' ? doRestore(ids) : doIgnore(ids))}>
                  {view === 'IGNORED' ? 'Restore selected' : 'Ignore selected'}
                  {view === 'TO_REVIEW' ? ` (${ids.length})` : ''}
                </Button>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
