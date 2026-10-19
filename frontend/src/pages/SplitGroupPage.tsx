import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams, useNavigate } from 'react-router';
import { ArrowLeft, ArrowDownLeft, ArrowUpRight, Check, CircleCheck, Clock, Link2, Plus, RefreshCw, Users, Wallet, Trash2, X } from 'lucide-react';
import { Button, buttonClass } from '../components/Button';
import { Alert, TextField } from '../components/FormParts';
import { CopyButton } from '../components/documents/CopyButton';
import { InvitationDialog, SplitExpenseForm, SplitPaymentForm } from '../components/splits/SplitForms';
import { ApiError, errorMessage } from '../lib/api';
import { inviteUrl, splitApi, splitMoney, type SplitGroup, type SplitInvitation } from '../lib/bill-splits';
import { useFeedback } from '../lib/feedback-context';
import { usePageTitle } from '../lib/usePageTitle';
export function SplitGroupPage() {
  const { groupId } = useParams();
  return <Group key={groupId} id={groupId!} />;
}
function Group({ id }: {
  id: string;
}) {
  const [group, setGroup] = useState<SplitGroup | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [busy, setBusy] = useState(false), [name, setName] = useState(''), [fields, setFields] = useState<Record<string, string>>({});
  const [params, setParams] = useSearchParams(), source = params.get('document');
  const [form, setForm] = useState<'expense' | 'payment' | null>(source ? 'expense' : null);
  const [invitation, setInvitation] = useState<(SplitInvitation & {
    memberId: string;
  }) | null>(null);
  const running = useRef(false), mounted = useRef(true), generation = useRef(0);
  const { confirm, toast } = useFeedback();
  const navigate = useNavigate();
  usePageTitle(group?.name ?? 'Split bills');
  useEffect(() => {
    mounted.current = true;
    void load();
    return () => {
      mounted.current = false;
      generation.current++;
    };
  }, [id]);
  async function load() {
    const request = ++generation.current;
    setLoading(true);
    setError('');
    try {
      const g = await splitApi.get(id);
      if (mounted.current && request === generation.current)
        setGroup(g);
    }
    catch (e) {
      if (mounted.current && request === generation.current)
        setError(errorMessage(e));
    }
    finally {
      if (mounted.current && request === generation.current)
        setLoading(false);
    }
  }
  async function action(work: () => Promise<void>) {
    if (running.current)
      return;
    running.current = true;
    generation.current++;
    setLoading(false);
    setBusy(true);
    setError('');
    setFields({});
    try {
      await work();
    }
    catch (e) {
      if (mounted.current) {
        setError(errorMessage(e));
        if (e instanceof ApiError)
          setFields(e.fieldErrors);
      }
    }
    finally {
      running.current = false;
      if (mounted.current)
        setBusy(false);
    }
  }
  function closeForm() {
    setForm(null);
    if (source) {
      const next = new URLSearchParams(params);
      next.delete('document');
      setParams(next, {
        replace: true
      });
    }
  }
  function saved(g: SplitGroup) {
    generation.current++;
    setLoading(false);
    setGroup(g);
    closeForm();
    toast('Group updated');
  }
  async function deleteGroup() {
    if (!group || running.current) return;
    if (!await confirm({
      title: `Delete “${group.name}”?`,
      message: 'This permanently deletes the group for everyone, including all expenses, shares, payments, balances and invitations—even if money is still owed. Your original bills stay in your locker. This cannot be undone.',
      confirmLabel: 'Delete group',
      danger: true,
    })) return;
    await action(async () => {
      await splitApi.delete(id);
      if (mounted.current) {
        toast('Group deleted');
        navigate('/splits', { replace: true });
      }
    });
  }
  async function add(e: FormEvent) {
    e.preventDefault();
    await action(async () => {
      const g = await splitApi.member(id, name);
      if (mounted.current) {
        setGroup(g);
        setName('');
        toast('Guest added. You can now create their invite link.');
      }
    });
  }
  async function invite(memberId: string) {
    await action(async () => {
      const i = await splitApi.invite(id, memberId);
      if (mounted.current)
        setInvitation({
          ...i, memberId
        });
    });
  }
  async function revoke(memberId: string) {
    if (!await confirm({
      title: 'Revoke invitation?', message: 'The current invitation link will stop working. You can create another one.', confirmLabel: 'Revoke link', danger: true
    }))
      return;
    await action(async () => {
      await splitApi.revoke(id, memberId);
      if (mounted.current) {
        setInvitation(null);
        toast('Invitation revoked');
      }
    });
  }
  async function voidExpense(expenseId: string) {
    if (!await confirm({
      title: 'Void this expense?', message: 'The expense stays in the history but no longer counts toward balances. This cannot be undone.', confirmLabel: 'Void expense', danger: true
    }))
      return;
    await action(async () => {
      const g = await splitApi.void(id, expenseId);
      if (mounted.current)
        setGroup(g);
    });
  }
  async function decide(paymentId: string, choice: 'confirm' | 'reject') {
    if (!await confirm({
      title: choice === 'confirm' ? 'Confirm you received this payment?' : 'Reject or cancel this payment?', message: choice === 'confirm' ? 'Confirm only after the money has reached you. This updates the group balances.' : 'Balances will stay unchanged.', confirmLabel: choice === 'confirm' ? 'Confirm received' : 'Reject / cancel', danger: choice === 'reject'
    }))
      return;
    await action(async () => {
      const g = await splitApi.decide(id, paymentId, choice);
      if (mounted.current)
        setGroup(g);
    });
  }
  if (!group)
    return <div className="mx-auto max-w-5xl space-y-4 p-6">
      <Link to="/splits" className={buttonClass()}>Back to groups</Link>
      {loading ? <p role="status">Loading group…</p> : <><Alert tone="error">
        {error || 'Group unavailable.'}
      </Alert><Button onClick={load} icon={RefreshCw}>Try again</Button></>}
    </div>;
  const person = (memberId: string) => group.members.find(m => m.id === memberId)?.name ?? 'Unknown person';
  const locked = group.settlements.some(s => s.status !== 'REJECTED');
  const canPay = group.members.some(m => m.balance < 0 && (group.owner || m.self)) && group.members.some(m => m.balance > 0);
  const total = group.expenses.filter(e => !e.voided).reduce((sum, e) => sum + e.amount, 0);
  const pending = group.settlements.filter(s => s.status === 'PENDING').length;
  return <div className="mx-auto max-w-6xl space-y-6 p-4 sm:p-6">

    <Link to="/splits" className="inline-flex items-center gap-2 text-sm text-slate-500 hover:text-brand-700"><ArrowLeft className="size-4" aria-hidden />All groups</Link>

    <header className="flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0">
        <h1 className="break-words text-2xl font-semibold">
          {group.name}
        </h1>
        <p className="mt-1 text-sm text-slate-500">{group.members.length} people · INR · {group.owner ? 'You manage expenses and invitations' : 'Managed by the group owner'}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button icon={RefreshCw} onClick={load} busy={loading} disabled={busy} aria-label="Refresh group">Refresh</Button>
        {canPay && <Button icon={Wallet} onClick={() => setForm('payment')} disabled={busy}>Record payment</Button>}
        {group.owner && <Button variant="primary" icon={Plus} onClick={() => setForm('expense')} disabled={busy}>Add expense</Button>}
      </div>
    </header>

    {error && <Alert tone="error">
      {error}
    </Alert>}

    {source && !group.owner && <Alert tone="info">Only this group's owner can add expenses. Choose another group to split your bill.</Alert>}

    <div className="grid gap-4 sm:grid-cols-3">
      <div className="rounded-xl border border-slate-200 bg-white p-5">
        <p className="text-sm text-slate-500">Shared expenses</p>
        <p className="mt-1 text-2xl font-semibold tabular-nums">
          {splitMoney(total)}
        </p>
      </div>
      <div className="rounded-xl border border-slate-200 bg-white p-5">
        <p className="text-sm text-slate-500">Your balance</p>
        {(() => {
          const b = group.members.find(m => m.self)?.balance ?? 0;
          return <p className="mt-1 text-lg font-semibold">
            {b < 0 ? `You owe ${splitMoney(-b)}` : b > 0 ? `You are owed ${splitMoney(b)}` : 'All settled'}
          </p>;
        })()}
      </div>
      <div className="rounded-xl border border-slate-200 bg-white p-5">
        <p className="text-sm text-slate-500">Payments awaiting confirmation</p>
        <p className="mt-1 flex items-center gap-2 text-2xl font-semibold">
          <Clock className="size-5 text-amber-600" aria-hidden />
          {pending}
        </p>
      </div>
    </div>

    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">

      <section className="min-w-0 rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold"><Users className="size-5" aria-hidden />People & balances</h2>
        <ul className="divide-y divide-slate-100">
          {group.members.map(m => <li key={m.id} className="space-y-2 py-3">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="break-words font-medium">
                  {m.name}
                  {m.self ? ' (you)' : ''}
                </p>
                <p className="mt-1 flex items-center gap-1 text-xs text-slate-500">
                  {m.joined ? <CircleCheck className="size-3" aria-hidden /> : <Clock className="size-3" aria-hidden />}
                  {m.owner ? 'Owner' : m.joined ? 'Joined' : 'Guest · not joined'}
                </p>
              </div>
              <div className="shrink-0 text-right text-sm">
                <p className="font-semibold tabular-nums">
                  {splitMoney(Math.abs(m.balance))}
                </p>
                <p className="mt-1 flex items-center justify-end gap-1 text-xs text-slate-500">
                  {m.balance < 0 ? <><ArrowUpRight className="size-3" aria-hidden />Owes</> : m.balance > 0 ? <><ArrowDownLeft className="size-3" aria-hidden />Is owed</> : <><Check className="size-3" aria-hidden />Settled</>}
                </p>
              </div>
            </div>
            {group.owner && !m.joined && <div className="flex flex-wrap gap-1">
              <Button size="sm" variant="ghost" icon={Link2} onClick={() => invite(m.id)} disabled={busy}>Create invite link</Button>
              <Button size="sm" variant="ghost" onClick={() => revoke(m.id)} disabled={busy}>Revoke link</Button>
            </div>}
          </li>)}
        </ul>

        {group.owner && <form noValidate onSubmit={add} className="mt-4 space-y-3 border-t border-slate-200 pt-4">
          <TextField label="Add a friend's name" placeholder="e.g. Aman" maxLength={80} value={name} onChange={e => setName(e.target.value)} error={fields.name} />
          <Button type="submit" icon={Plus} busy={busy}>Add guest</Button>
          <p className="text-xs text-slate-500">Guests can be included before they join. Create a personal invite link to connect their account.</p>
        </form>}
      </section>

      <div className="min-w-0 space-y-6">

        <section className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-lg font-semibold">Expenses</h2>
          {!group.expenses.length ? <p className="mt-4 text-sm text-slate-500">Add your friends, then add an expense or choose “Split bill” on a saved bill.</p> : <ul className="mt-2 divide-y divide-slate-100">
            {group.expenses.map(e => <li key={e.id} className="py-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className={`break-words font-medium ${e.voided ? 'line-through text-slate-400' : ''}`}>
                    {e.title}
                  </h3>
                  <p className="mt-1 text-xs text-slate-500">{e.expenseDate} · Paid by {person(e.payerId)} · {e.mode === 'EQUAL' ? 'Equal split' : 'Custom split'}</p>
                  {(e.sellerName || e.documentNumber) && <p className="mt-1 break-words text-xs text-slate-500">Bill: {[e.sellerName, e.documentNumber].filter(Boolean).join(' · ')}</p>}
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-semibold tabular-nums">
                    {splitMoney(e.amount)}
                  </span>
                  {e.voided ? <span className="flex items-center gap-1 text-xs text-slate-500"><X className="size-3" aria-hidden />Voided</span> : group.owner && <Button size="sm" variant="dangerGhost" disabled={busy || locked} title={locked ? 'Expenses are locked because payments are pending or confirmed' : undefined} onClick={() => voidExpense(e.id)}>Void</Button>}
                </div>
              </div>
              <details className="mt-3 text-sm">
                <summary className="cursor-pointer text-brand-700">View shares</summary>
                <ul className="mt-2 space-y-1 rounded-lg bg-slate-50 p-3">
                  {e.shares.map(s => <li key={s.memberId} className="flex justify-between gap-3">
                    <span className="break-words">
                      {person(s.memberId)}
                    </span>
                    <span className="shrink-0 tabular-nums">
                      {splitMoney(s.amount ?? 0)}
                    </span>
                  </li>)}
                </ul>
              </details>
            </li>)}
          </ul>}
          {locked && <p className="mt-3 text-xs text-slate-500">Expenses are locked while any payment is pending or confirmed.</p>}
        </section>

        <section className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-lg font-semibold">Payments</h2>
          <p className="mt-1 text-xs text-slate-500">Recorded manually. Only confirmed payments affect balances. The owner can confirm for a guest who hasn't joined.</p>
          {!group.settlements.length ? <p className="mt-4 text-sm text-slate-500">No payments recorded yet.</p> : <ul className="mt-2 divide-y divide-slate-100">
            {group.settlements.map(s => <li key={s.id} className="space-y-2 py-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="break-words text-sm"><strong>
                  {person(s.fromMemberId)}
                </strong> → <strong>
                    {person(s.toMemberId)}
                  </strong></p>
                <span className="font-semibold tabular-nums">
                  {splitMoney(s.amount)}
                </span>
              </div>
              <p className="flex items-center gap-1.5 text-xs text-slate-500">{s.status === 'CONFIRMED' ? <CircleCheck className="size-4 text-emerald-600" aria-hidden /> : s.status === 'PENDING' ? <Clock className="size-4 text-amber-600" aria-hidden /> : <X className="size-4" aria-hidden />}{s.status === 'PENDING' ? 'Awaiting confirmation' : s.status === 'CONFIRMED' ? 'Confirmed' : 'Rejected / cancelled'} · {new Date(s.createdAt).toLocaleDateString()}</p>
              <div className="flex flex-wrap gap-2">
                {s.canConfirm && <Button size="sm" icon={Check} disabled={busy} onClick={() => decide(s.id, 'confirm')}>Confirm received</Button>}
                {s.canReject && <Button size="sm" variant="ghost" disabled={busy} onClick={() => decide(s.id, 'reject')}>Reject / cancel</Button>}
              </div>
            </li>)}
          </ul>}
        </section>

      </div>

    </div>

    <section className="rounded-xl border border-slate-200 bg-white p-5">
      <details>
        <summary className="cursor-pointer font-semibold">Group activity</summary>
        <ol className="mt-4 space-y-3">
          {group.activity.map(a => <li key={a.id} className="flex flex-wrap justify-between gap-2 text-sm">
            <span className="break-words">
              {a.message}
            </span>
            <time className="text-xs text-slate-400" dateTime={a.createdAt}>
              {new Date(a.createdAt).toLocaleString()}
            </time>
          </li>)}
        </ol>
      </details>
    </section>

    {group.owner && <section className="rounded-xl border border-rose-200 bg-white p-5" aria-label="Delete group">
      <h2 className="font-semibold">Delete group</h2>
      <p className="mt-1 text-sm text-slate-500">Permanently remove this group and its shared history for everyone. Original bills stay in your locker.</p>
      <Button className="mt-4" variant="dangerGhost" icon={Trash2} onClick={deleteGroup} busy={busy} disabled={loading}>Delete group</Button>
    </section>}
    <CopyButton text={`${group.name} — balances\n${group.members.map(m => `${m.name}: ${m.balance < 0 ? 'owes' : m.balance > 0 ? 'is owed' : 'settled'} ${splitMoney(Math.abs(m.balance))}`).join('\n')}`} label="Copy group balances">Copy balances</CopyButton>

    {form === 'expense' && group.owner && <SplitExpenseForm group={group} source={source} onClose={closeForm} onSaved={saved} />}

    {form === 'payment' && <SplitPaymentForm group={group} onClose={closeForm} onSaved={saved} />}

    {invitation && <InvitationDialog error={error} name={invitation.memberName} url={inviteUrl(invitation.token)} expires={invitation.expiresAt} onClose={() => setInvitation(null)} onRevoke={() => revoke(invitation.memberId)} busy={busy} />}

  </div>;
}
