import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { ApiError, errorMessage } from '../../lib/api';
import { listDocuments, type DocumentSummary } from '../../lib/documents';
import { today } from '../../lib/expenses';
import { useFeedback } from '../../lib/feedback-context';
import { createServiceRecord, updateServiceRecord, SERVICE_TYPES, type ServiceProduct, type ServiceRecord, type ServiceRecordInput, type ServiceType, type ClaimStatus } from '../../lib/service-records';
import { Button } from '../Button';
import { Alert, TextField } from '../FormParts';
import { CopyButton } from '../documents/CopyButton';

type Values = { type: ServiceType; title: string; serviceDate: string; providerName: string; providerContact: string;
  cost: string; coveredByWarranty: boolean; claimNumber: string; claimStatus: string; nextDueDate: string;
  repeatMonths: string; description: string; attachmentDocumentId: string };

export function ServiceRecordForm({ product, record, onSaved, onClose }: {
  product: ServiceProduct; record?: ServiceRecord; onSaved: () => void; onClose: () => void;
}) {
  const { confirm } = useFeedback();
  const dialog = useRef<HTMLDialogElement>(null);
  const form = useRef<HTMLFormElement>(null);
  const locked = useRef(false);
  const notesId = useId();
  const [value, setValue] = useState<Values>({ type: record?.type ?? 'ROUTINE_SERVICE', title: record?.title ?? '',
    serviceDate: record?.serviceDate ?? today(), providerName: record?.providerName ?? '', providerContact: record?.providerContact ?? '',
    cost: record?.cost == null ? '' : String(record.cost), coveredByWarranty: record?.coveredByWarranty ?? false,
    claimNumber: record?.claimNumber ?? '', claimStatus: record?.claimStatus ?? '', nextDueDate: record?.nextDueDate ?? '',
    repeatMonths: record?.repeatMonths == null ? '' : String(record.repeatMonths), description: record?.description ?? '', attachmentDocumentId: record?.attachmentDocumentId ?? '' });
  const [schedule, setSchedule] = useState(record?.repeatMonths ? 'repeat' : record?.nextDueDate ? 'date' : 'none');
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<Record<string,string>>({});
  const [error, setError] = useState<string | null>(null);
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [attachmentError, setAttachmentError] = useState(false);
  useEffect(() => {
    dialog.current?.showModal();
    form.current?.querySelector<HTMLInputElement>('input')?.focus();
    let current = true;
    listDocuments({}).then((list) => { if (current) setDocuments(list); }).catch(() => { if (current) setAttachmentError(true); });
    return () => { current = false; };
  }, []);
  useEffect(() => {
    const save = (e: KeyboardEvent) => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') { e.preventDefault(); if (!busy) form.current?.requestSubmit(); } };
    document.addEventListener('keydown', save);
    return () => document.removeEventListener('keydown', save);
  }, [busy]);
  function set<K extends keyof Values>(key: K, next: Values[K]) { setDirty(true); setValue((v) => ({ ...v, [key]: next })); }
  async function close() {
    if (busy) return;
    if (dirty && !(await confirm({ title: 'Discard service record changes?', message: 'Your unsaved changes will be lost.', confirmLabel: 'Discard changes', danger: true }))) return;
    onClose();
  }
  async function save(e: FormEvent) {
    e.preventDefault(); if (locked.current) return; locked.current = true; setBusy(true); setError(null); setErrors({});
    if ((schedule === 'repeat' && !value.repeatMonths.trim()) || (schedule === 'date' && !value.nextDueDate)) {
      setErrors(schedule === 'repeat' ? { repeatMonths: 'Enter a number from 1 to 60.' } : { nextDueDate: 'Choose the next service date.' });
      locked.current = false; setBusy(false); return;
    }
    const text = (s: string) => s.trim() || null;
    const request: ServiceRecordInput = { ...value, title: value.title.trim(), cost: value.cost === '' ? null : Number(value.cost),
      providerName: text(value.providerName), providerContact: text(value.providerContact), description: text(value.description),
      claimNumber: value.type === 'WARRANTY_CLAIM' ? text(value.claimNumber) : null,
      claimStatus: value.type === 'WARRANTY_CLAIM' ? (value.claimStatus || null) as ClaimStatus | null : null,
      nextDueDate: schedule === 'date' ? text(value.nextDueDate) : null,
      repeatMonths: schedule === 'repeat' && value.repeatMonths !== '' ? Number(value.repeatMonths) : null,
      attachmentDocumentId: value.attachmentDocumentId || null };
    try {
      if (record) await updateServiceRecord(record.id, request); else await createServiceRecord(product, request);
      onSaved();
    } catch (e) {
      setError(errorMessage(e)); setErrors(e instanceof ApiError ? e.fieldErrors : {});
      requestAnimationFrame(() => form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
    } finally { locked.current = false; setBusy(false); }
  }
  const field = (name: Exclude<keyof Values, 'coveredByWarranty' | 'type'>, label: string, type = 'text') => <TextField label={label} type={type} value={value[name]} onChange={(e) => set(name, e.target.value)} error={errors[name]} step={type === 'number' ? 'any' : undefined} trailing={<CopyButton text={value[name]} label={`Copy ${label.toLowerCase()}`} />} />;
  const selectClass = 'mt-1.5 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm';
  return <dialog ref={dialog} aria-labelledby={`${notesId}-title`} onCancel={(e) => { e.preventDefault(); void close(); }} className="fixed inset-0 m-auto max-h-[90dvh] w-[calc(100%_-_2rem)] max-w-2xl overflow-y-auto rounded-2xl border border-slate-200 bg-white p-5 shadow-xl backdrop:bg-slate-900/40">
    <h2 id={`${notesId}-title`} className="text-lg font-semibold">{record ? 'Edit service record' : 'Add service record'}</h2>
    <form ref={form} onSubmit={save} noValidate className="mt-4 space-y-4">
      {error && <Alert tone="error">{error}</Alert>}
      <fieldset disabled={busy} className="grid gap-4 sm:grid-cols-2">
        <label className="text-sm font-medium text-slate-700">Type<select className={selectClass} value={value.type} onChange={(e) => set('type', e.target.value as ServiceType)} aria-invalid={Boolean(errors.type)}>{Object.entries(SERVICE_TYPES).map(([key,label]) => <option key={key} value={key}>{label}</option>)}</select>{errors.type && <span className="text-xs text-rose-700">{errors.type}</span>}</label>
        {field('serviceDate', 'Service date', 'date')}
        <div className="sm:col-span-2">{field('title', 'Title')}</div>
        {field('providerName', 'Provider')}{field('providerContact', 'Provider contact')}
        {field('cost', 'Cost (INR)', 'number')}
        <label className="flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={value.coveredByWarranty} onChange={(e) => set('coveredByWarranty',e.target.checked)} />Covered by warranty</label>
        {value.type === 'WARRANTY_CLAIM' && <>{field('claimNumber', 'Claim number')}<label className="text-sm font-medium text-slate-700">Claim status<select className={selectClass} value={value.claimStatus} onChange={(e) => set('claimStatus',e.target.value)}><option value="">Not set</option><option value="OPEN">Open</option><option value="RESOLVED">Resolved</option><option value="REJECTED">Rejected</option></select></label></>}
        <label className="text-sm font-medium text-slate-700">Next service<select className={selectClass} value={schedule} onChange={(e) => { setSchedule(e.target.value); setDirty(true); }}><option value="none">No reminder</option><option value="date">Choose a date</option><option value="repeat">Repeat every few months</option></select></label>
        {schedule === 'date' && field('nextDueDate','Next due date','date')}
        {schedule === 'repeat' && field('repeatMonths','Repeat every (1–60 months)','number')}
        <label className="text-sm font-medium text-slate-700 sm:col-span-2">Attached document (optional)<select className={selectClass} value={value.attachmentDocumentId} onChange={(e) => set('attachmentDocumentId',e.target.value)} aria-invalid={Boolean(errors.attachmentDocumentId)}><option value="">None</option>{documents.map((d) => <option key={d.id} value={d.id}>{d.fileName}</option>)}</select>{attachmentError && <span className="text-xs text-amber-700">Could not load attachments. Close and reopen to try again.</span>}{errors.attachmentDocumentId && <span className="text-xs text-rose-700">{errors.attachmentDocumentId}</span>}</label>
        <div className="sm:col-span-2"><div className="flex items-center justify-between"><label htmlFor={notesId} className="text-sm font-medium text-slate-700">Notes</label><CopyButton text={value.description} label="Copy notes" /></div><textarea id={notesId} rows={3} className={selectClass} value={value.description} onChange={(e) => set('description',e.target.value)} aria-invalid={Boolean(errors.description)} aria-describedby={errors.description ? `${notesId}-error` : undefined} />{errors.description && <p id={`${notesId}-error`} className="text-xs text-rose-700">{errors.description}</p>}</div>
      </fieldset>
      <div className="flex justify-end gap-2"><Button disabled={busy} onClick={() => void close()}>Cancel</Button><Button variant="primary" type="submit" busy={busy}>{busy ? 'Saving...' : 'Save service record'}</Button></div>
    </form>
  </dialog>;
}
