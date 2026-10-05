import { EyeOff, Hand, Lock, Mail, ScanSearch, ShieldCheck, Sparkles, Unplug } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/toast-context';
import { useConnectGmail } from '@/hooks/useGmail';
import { getErrorMessage } from '@/lib/api-client';
import { isSafeAuthorizationUrl } from './gmail-utils';

const PROMISES = [
  { icon: EyeOff, title: 'Read-only access', text: 'Bill Locker can never send, delete or change your emails.' },
  { icon: ScanSearch, title: 'Only bills surface', text: 'Bill Locker shortlists invoices, receipts and warranty cards — nothing else is shown.' },
  { icon: Hand, title: 'You stay in control', text: 'Nothing is imported until you choose it, and every extraction waits for your review.' },
  { icon: Unplug, title: 'Disconnect anytime', text: 'Access tokens are kept encrypted on the server and revoked when you disconnect.' },
];

const STEPS = ['Connect your Gmail', 'AI scans for bills & invoices', 'Review and import in one click'];

/** Shown when Gmail is not connected: explains the value and the permissions first. */
export function GmailConnectPanel() {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const connect = useConnectGmail();
  const toast = useToast();

  async function continueToGoogle() {
    try {
      const { authorizationUrl } = await connect.mutateAsync();
      if (!isSafeAuthorizationUrl(authorizationUrl)) {
        throw new Error('Received an unexpected sign-in address. Please try again.');
      }
      window.location.assign(authorizationUrl);
    } catch (error) {
      setConfirmOpen(false);
      toast.error('Could not start the Gmail connection', getErrorMessage(error));
    }
  }

  return (
    <>
      <Card className="overflow-hidden">
        <div className="grid lg:grid-cols-[1.25fr_1fr]">
          <div className="p-6 sm:p-8">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700">
              <Sparkles className="size-3.5" aria-hidden />
              No more manual uploads
            </span>
            <h2 className="mt-4 text-xl font-semibold tracking-tight text-balance text-slate-900 sm:text-2xl">
              Let Bill Locker find the bills already sitting in your inbox
            </h2>
            <p className="mt-2 max-w-xl text-sm text-slate-600 sm:text-base">
              Order confirmations, invoices and warranty certificates usually arrive by email. Connect Gmail and Bill
              Locker gathers them for you — ready to review and save.
            </p>

            <ul className="mt-6 grid gap-4 sm:grid-cols-2">
              {PROMISES.map(({ icon: Icon, title, text }) => (
                <li key={title} className="flex gap-3">
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
                    <Icon className="size-4" aria-hidden />
                  </span>
                  <span>
                    <span className="block text-sm font-semibold text-slate-900">{title}</span>
                    <span className="block text-sm text-slate-500">{text}</span>
                  </span>
                </li>
              ))}
            </ul>

            <Button
              size="lg"
              className="mt-8"
              onClick={() => setConfirmOpen(true)}
              leftIcon={<Mail className="size-5" aria-hidden />}
            >
              Connect Gmail
            </Button>
          </div>

          <div className="border-t border-slate-100 bg-slate-50/80 p-6 sm:p-8 lg:border-t-0 lg:border-l">
            <p className="text-sm font-semibold text-slate-900">How it works</p>
            <ol className="mt-4 space-y-4">
              {STEPS.map((step, index) => (
                <li key={step} className="flex items-center gap-3">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-white text-sm font-semibold text-brand-700 shadow-card">
                    {index + 1}
                  </span>
                  <span className="text-sm text-slate-700">{step}</span>
                </li>
              ))}
            </ol>
            <div className="mt-6 flex items-start gap-2 rounded-xl bg-white p-3 text-xs text-slate-500 shadow-card">
              <Lock className="mt-0.5 size-3.5 shrink-0 text-slate-400" aria-hidden />
              We request the <code className="font-mono text-[11px] text-slate-700">gmail.readonly</code> scope only. Your
              emails are never used to train AI models.
            </div>
          </div>
        </div>
      </Card>

      <Modal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        size="sm"
        dismissible={!connect.isPending}
        title="Connect your Gmail account"
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmOpen(false)} disabled={connect.isPending}>
              Cancel
            </Button>
            <Button onClick={() => void continueToGoogle()} loading={connect.isPending} data-autofocus>
              Continue to Google
            </Button>
          </>
        }
      >
        <div className="space-y-3 text-sm text-slate-600">
          <p>You’ll be redirected to Google to approve access. Bill Locker will be able to:</p>
          <ul className="space-y-2">
            <li className="flex gap-2">
              <ShieldCheck className="mt-0.5 size-4 shrink-0 text-emerald-600" aria-hidden />
              Read emails that look like bills, invoices or warranty documents
            </li>
            <li className="flex gap-2">
              <ShieldCheck className="mt-0.5 size-4 shrink-0 text-emerald-600" aria-hidden />
              Download their attachments when you choose to import them
            </li>
          </ul>
          <p className="text-xs text-slate-500">It will never send, delete or modify anything in your mailbox.</p>
        </div>
      </Modal>
    </>
  );
}
