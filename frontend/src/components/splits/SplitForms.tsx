import { useEffect, useId, useRef, useState, type ReactNode, type FormEvent } from 'react';
import { X, Info } from 'lucide-react';
import { Button } from '../Button';
import { Alert, TextField } from '../FormParts';
import { CopyButton } from '../documents/CopyButton';
import { ApiError, errorMessage } from '../../lib/api';
import { getDocument } from '../../lib/documents';
import { equalShares, splitApi, splitMoney, type SplitGroup } from '../../lib/bill-splits';
const control = 'mt-1.5 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm';
export function SplitSelect({ label, value, onChange, children, error }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  children: ReactNode;
  error?: string;
}) {
  const id = useId();
  return <div>
    <label htmlFor={id} className="block text-sm font-medium text-slate-700">
      {label}
    </label>
    <select id={id} className={control} value={value} onChange={e => onChange(e.target.value)} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined}>
      {children}
    </select>
    {error && <p id={`${id}-error`} className="mt-1 text-xs text-rose-600">
      {error}
    </p>}
  </div>;
}
function Modal({ title, onClose, children, busy }: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  busy: boolean;
}) {
  const dialog = useRef<HTMLDialogElement>(null), heading = useId();
  useEffect(() => {
    const el = dialog.current;
    el?.showModal();
    return () => {
      el?.close();
    };
  }, []);
  return <dialog ref={dialog} aria-labelledby={heading} onCancel={e => {
    e.preventDefault();
    if (!busy)
      onClose();
  }} className="m-auto max-h-[90dvh] w-[calc(100%_-_2rem)] max-w-xl overflow-y-auto rounded-2xl bg-white p-5 text-slate-900 shadow-xl backdrop:bg-slate-950/40 sm:p-7">
    <div className="mb-5 flex items-center justify-between gap-4">
      <h2 id={heading} className="text-xl font-semibold">
        {title}
      </h2>
      <Button icon={X} variant="ghost" aria-label="Close form" disabled={busy} onClick={onClose} />
    </div>
    {children}
  </dialog>;
}
function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
export function SplitExpenseForm({ group, source, onClose, onSaved }: {
  group: SplitGroup;
  source: string | null;
  onClose: () => void;
  onSaved: (group: SplitGroup) => void;
}) {
  const [title, setTitle] = useState(''), [amount, setAmount] = useState(''), [date, setDate] = useState(today);
  const [payer, setPayer] = useState(group.members.find(m => m.self)?.id ?? group.members[0].id), [mode, setMode] = useState<'EQUAL' | 'CUSTOM'>('EQUAL');
  const [selected, setSelected] = useState<string[]>(group.members.map(m => m.id)), [custom, setCustom] = useState<Record<string, string>>({});
  const [sourceReady, setSourceReady] = useState(!source), [sourceError, setSourceError] = useState('');
  const [sourceTitle, setSourceTitle] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [fields, setFields] = useState<Record<string, string>>({});
  const requestId = useRef(crypto.randomUUID()), running = useRef(false);
  useEffect(() => {
    if (!source)
      return;
    let active = true;
    getDocument(source).then(d => {
      if (!active)
        return;
      if (d.status !== 'SAVED') {
        setSourceError('Save the bill before splitting it.');
        return;
      }
      setTitle(d.sellerName ? `${d.sellerName} bill` : 'Shared bill');
      setAmount(d.totalAmount === null ? '' : String(d.totalAmount));
      setDate(d.purchaseDate ?? today());
      setSourceTitle([d.sellerName, d.documentNumber].filter(Boolean).join(' · ') || d.fileName);
      setSourceReady(true);
    }).catch(e => {
      if (active)
        setSourceError(errorMessage(e));
    });
    return () => {
      active = false;
    };
  }, [source]);
  const preview = mode === 'EQUAL' ? equalShares(Number(amount), selected) : selected.map(memberId => ({
    memberId, amount: Number(custom[memberId] ?? 0)
  }));
  const shareTotal = preview.reduce((sum, s) => sum + Math.round((s.amount ?? 0) * 100), 0) / 100;
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (running.current || !sourceReady)
      return;
    running.current = true;
    setBusy(true);
    setError('');
    setFields({});
    try {
      const next = await splitApi.expense(group.id, {
        requestId: requestId.current, title, amount: amount.trim() ? Number(amount) : null, expenseDate: date, payerId: payer, mode,
        shares: selected.map(memberId => ({
          memberId, amount: mode === 'CUSTOM' && custom[memberId]?.trim() ? Number(custom[memberId]) : null
        })), ...(source ? {
          sourceDocumentId: source
        } : {})
      });
      onSaved(next);
    }
    catch (e) {
      setError(errorMessage(e));
      if (e instanceof ApiError)
        setFields(e.fieldErrors);
    }
    finally {
      running.current = false;
      setBusy(false);
    }
  }
  return <Modal title="Add shared expense" onClose={onClose} busy={busy}>
    <form noValidate onSubmit={submit} onKeyDown={e => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        e.currentTarget.requestSubmit();
      }
    }} className="space-y-4">

      {error && <Alert tone="error">
        {error}
      </Alert>}
      {sourceError && <Alert tone="error">{sourceError} Close this form to choose another bill or add an expense manually.</Alert>}

      {source && !sourceReady && !sourceError && <p role="status">Loading your saved bill…</p>}

      {sourceReady && source && <Alert tone="info">From {sourceTitle}. Only the shop, bill number and expense details are shared. The file and buyer details stay private.</Alert>}

      <fieldset disabled={busy || !sourceReady} className="space-y-4">

        <TextField label="Expense title" value={title} maxLength={200} onChange={e => setTitle(e.target.value)} error={fields.title} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Total (₹)" type="number" min="0.01" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} error={fields.amount} />
          <TextField label="Date" type="date" max={today()} value={date} onChange={e => setDate(e.target.value)} error={fields.expenseDate} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <SplitSelect label="Paid by" value={payer} onChange={setPayer} error={fields.payerId}>
            {group.members.map(m => <option key={m.id} value={m.id}>
              {m.name}
              {m.self ? ' (you)' : ''}
            </option>)}
          </SplitSelect>
          <SplitSelect label="Split method" value={mode} onChange={v => setMode(v as 'EQUAL' | 'CUSTOM')} error={fields.mode}>
            <option value="EQUAL">Equally</option>
            <option value="CUSTOM">Custom amounts</option>
          </SplitSelect>
        </div>

        <fieldset className="rounded-lg border border-slate-200 p-3">
          <legend className="px-1 text-sm font-medium">Split between</legend>
          <div className="space-y-3">
            {group.members.map(m => <div key={m.id} className="flex flex-wrap items-center justify-between gap-3">
              <label className="flex min-w-0 flex-1 items-center gap-2 text-sm">
                <input type="checkbox" className="size-4 accent-brand-600" checked={selected.includes(m.id)} onChange={e => setSelected(ids => e.target.checked ? [...ids, m.id] : ids.filter(id => id !== m.id))} />
                <span className="break-words">
                  {m.name}
                  {m.self ? ' (you)' : ''}
                </span>
              </label>
              {selected.includes(m.id) && (mode === 'CUSTOM' ? <div className="w-32">
                <TextField label={`${m.name}'s share (₹)`} type="number" min="0" step="0.01" value={custom[m.id] ?? ''} onChange={e => setCustom(c => ({
                  ...c, [m.id]: e.target.value
                }))} />
              </div> : <span className="text-sm tabular-nums">
                {splitMoney(preview.find(s => s.memberId === m.id)?.amount ?? 0)}
              </span>)}
            </div>)}
          </div>
        </fieldset>

        {fields.shares && <p role="alert" className="text-sm text-rose-600">
          {fields.shares}
        </p>}

        {Object.entries(fields).filter(([key]) => key.startsWith('shares[')).map(([key, message]) => <p role="alert" key={key} className="text-sm text-rose-600">
          {message}
        </p>)}

        <p className="text-sm text-slate-500">Allocated: {splitMoney(shareTotal)} of {splitMoney(Number(amount) || 0)}. {mode === 'EQUAL' ? 'Any remaining paise are assigned automatically.' : 'Custom shares must match the total.'}</p>

        <p className="flex gap-2 text-xs text-slate-500"><Info className="size-4 shrink-0" aria-hidden />Expenses can be voided until the first pending or confirmed payment. They cannot be edited.</p>

      </fieldset>

      <div className="flex flex-wrap justify-end gap-2">
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button type="submit" variant="primary" busy={busy} disabled={!sourceReady}>Save expense</Button>
      </div>

    </form>
  </Modal>;
}
export function SplitPaymentForm({ group, onClose, onSaved }: {
  group: SplitGroup;
  onClose: () => void;
  onSaved: (g: SplitGroup) => void;
}) {
  const debtors = group.members.filter(m => m.balance < 0 && (group.owner || m.self)), creditors = group.members.filter(m => m.balance > 0);
  const [from, setFrom] = useState(debtors[0]?.id ?? ''), [to, setTo] = useState(creditors[0]?.id ?? ''), [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [fields, setFields] = useState<Record<string, string>>({});
  const running = useRef(false), requestId = useRef(crypto.randomUUID());
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (running.current)
      return;
    running.current = true;
    setBusy(true);
    setError('');
    setFields({});
    try {
      onSaved(await splitApi.payment(group.id, {
        requestId: requestId.current, fromMemberId: from, toMemberId: to, amount: amount.trim() ? Number(amount) : null
      }));
    }
    catch (e) {
      setError(errorMessage(e));
      if (e instanceof ApiError)
        setFields(e.fieldErrors);
    }
    finally {
      running.current = false;
      setBusy(false);
    }
  }
  const max = Math.min(-(debtors.find(m => m.id === from)?.balance ?? 0), creditors.find(m => m.id === to)?.balance ?? 0);
  return <Modal title="Record a payment" onClose={onClose} busy={busy}>
    <form noValidate onSubmit={submit} onKeyDown={e => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        e.currentTarget.requestSubmit();
      }
    }} className="space-y-4">

      {error && <Alert tone="error">
        {error}
      </Alert>}
      <Alert tone="info">This records money paid outside Bill Locker. It does not transfer money. Balances change only after the recipient confirms.</Alert>

      <fieldset disabled={busy} className="space-y-4">
        <SplitSelect label="Paid by" value={from} onChange={setFrom} error={fields.fromMemberId}>
          {debtors.map(m => <option key={m.id} value={m.id}>{m.name} · owes {splitMoney(-m.balance)}</option>)}
        </SplitSelect>
        <SplitSelect label="Paid to" value={to} onChange={setTo} error={fields.toMemberId}>
          {creditors.map(m => <option key={m.id} value={m.id}>{m.name} · owed {splitMoney(m.balance)}</option>)}
        </SplitSelect>
        <TextField label="Amount paid (₹)" type="number" min="0.01" step="0.01" max={max} value={amount} onChange={e => setAmount(e.target.value)} error={fields.amount} hint={`Up to ${splitMoney(max)}`} />
        <Button size="sm" onClick={() => setAmount(max.toFixed(2))}>Use full outstanding amount</Button>
      </fieldset>

      <div className="flex justify-end gap-2">
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button type="submit" variant="primary" busy={busy} disabled={!from || !to}>Record payment</Button>
      </div>

    </form>
  </Modal>;
}
export function InvitationDialog({ name, url, expires, onClose, onRevoke, busy, error }: {
  name: string;
  url: string;
  expires: string;
  error?: string;
  onClose: () => void;
  onRevoke: () => void;
  busy: boolean;
}) {
  return <Modal title={`Invite ${name}`} onClose={onClose} busy={busy}>
    <div className="space-y-4">
      {error && <Alert tone="error">{error}</Alert>}
      <p>Send this link to <strong>
        {name}
      </strong>. They can sign in or create an account, then accept to join this group.</p>
      <Alert tone="info">Anyone with this link can claim this person's place. Share it directly with them. Creating another link invalidates this one.</Alert>
      <TextField label="Invitation link" value={url} readOnly onFocus={e => e.target.select()} trailing={<CopyButton text={url} label="Copy invitation link" />} />
      <p className="text-xs text-slate-500">Expires {new Date(expires).toLocaleString()}</p>
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="dangerGhost" onClick={onRevoke} busy={busy}>Revoke invitation</Button>
        <Button onClick={onClose} disabled={busy}>Done</Button>
      </div>
    </div>
  </Modal>;
}
