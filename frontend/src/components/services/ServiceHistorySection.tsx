import { useState } from 'react';
import { Plus } from 'lucide-react';
import { Button } from '../Button';
import { ServiceTimeline } from './ServiceTimeline';
import { ServiceRecordForm } from './ServiceRecordForm';
import { deleteServiceRecord, type ServiceRecord } from '../../lib/service-records';
import type { ProductView } from '../../lib/products';
import { formatDate } from '../../lib/document-form';
import { formatAmount } from '../../lib/documents';
import { useFeedback } from '../../lib/feedback-context';
import { errorMessage } from '../../lib/api';

export function ServiceHistorySection({ product: p, onChanged }: { product: ProductView; onChanged: () => void }) {
  const { confirm, toast } = useFeedback();
  const [editing, setEditing] = useState<ServiceRecord | 'new' | null>(null);
  const [busy, setBusy] = useState(false);
  async function remove(r: ServiceRecord) {
    if (busy || !(await confirm({ title: 'Delete this service record?', message: r.title, confirmLabel: 'Delete service record', danger: true }))) return;
    setBusy(true);
    try { await deleteServiceRecord(r.id); onChanged(); toast('Service record deleted.'); }
    catch (e) { toast(errorMessage(e),'error'); }
    finally { setBusy(false); }
  }
  return <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="font-semibold">Service history</h2>{p.bill.status === 'SAVED' && <Button size="sm" icon={Plus} disabled={busy} onClick={() => setEditing('new')}>Add service record</Button>}</div>
    <dl className="mt-4 grid grid-cols-1 gap-3 text-sm sm:grid-cols-3">
      <div><dt className="text-slate-500">Last service</dt><dd className="font-medium">{p.lastServiceDate ? formatDate(p.lastServiceDate) : 'None recorded'}</dd></div>
      <div><dt className="text-slate-500">Next service due</dt><dd className="font-medium">{p.nextServiceDue ? formatDate(p.nextServiceDue) : 'Not scheduled'}</dd></div>
      <div><dt className="text-slate-500">Total service cost</dt><dd className="font-medium">{formatAmount(p.totalServiceCost)}</dd></div>
    </dl>
    {p.bill.status !== 'SAVED' && <p className="mt-3 text-sm text-amber-700">Save and review the bill to manage service records.</p>}
    <div className="mt-5"><ServiceTimeline records={p.serviceHistory} onEdit={p.bill.status === 'SAVED' ? setEditing : undefined} onDelete={remove} busy={busy} /></div>
    {editing && <ServiceRecordForm product={p} record={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); onChanged(); toast('Service record saved.'); }} />}
  </section>;
}
