import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { ErrorState } from '../components/ErrorState';
import { ServiceTimeline } from '../components/services/ServiceTimeline';
import { ServiceRecordForm } from '../components/services/ServiceRecordForm';
import { errorMessage } from '../lib/api';
import { useFeedback } from '../lib/feedback-context';
import { deleteServiceRecord, listServiceRecords, SERVICE_TYPES, type ServiceRecord } from '../lib/service-records';
import { usePageTitle } from '../lib/usePageTitle';

export function ServicesPage() {
  usePageTitle('Services');
  const { confirm, toast } = useFeedback();
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? ''; const type = params.get('type') ?? ''; const due = params.get('due') ?? '';
  const [records, setRecords] = useState<ServiceRecord[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<ServiceRecord | null>(null);
  const [busy, setBusy] = useState(false);
  const latest = useRef(0);
  const load = useCallback(() => {
    const request = ++latest.current; setError(null);
    listServiceRecords({q,type,due}).then((list) => { if (request === latest.current) setRecords(list); })
      .catch((e) => { if (request === latest.current) setError(errorMessage(e)); });
  }, [q,type,due]);
  useEffect(() => { const timer = setTimeout(load,300); return () => { clearTimeout(timer); ++latest.current; }; }, [load]);
  function filter(name: string, value: string) { setParams((current) => { const next = new URLSearchParams(current); if (value) next.set(name,value); else next.delete(name); return next; }, {replace:true}); }
  async function remove(r: ServiceRecord) {
    if (busy || !(await confirm({ title:'Delete this service record?', message:r.title, confirmLabel:'Delete service record', danger:true }))) return;
    setBusy(true);
    try { await deleteServiceRecord(r.id); load(); toast('Service record deleted.'); } catch (e) { toast(errorMessage(e),'error'); } finally { setBusy(false); }
  }
  const input = 'mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm';
  return <main className="mx-auto max-w-6xl px-4 py-8">
    <h1 className="text-2xl font-semibold tracking-tight">Services</h1><p className="mt-1 text-slate-600">Repairs, maintenance and warranty claims. Open a product to add a service record.</p>
    <div className="mt-6 grid gap-3 sm:grid-cols-[1fr_auto_auto]">
      <label className="text-sm text-slate-700">Search services<input type="search" className={input} value={q} onChange={(e) => filter('q',e.target.value)} placeholder="Product, provider, title or claim number" /></label>
      <label className="text-sm text-slate-700">Type<select className={input} value={type} onChange={(e) => filter('type',e.target.value)}><option value="">All types</option>{Object.entries(SERVICE_TYPES).map(([key,label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <label className="text-sm text-slate-700">Next service<select className={input} value={due} onChange={(e) => filter('due',e.target.value)}><option value="">All records</option><option value="DUE_SOON">Due within 30 days</option><option value="OVERDUE">Overdue</option><option value="UPCOMING">Upcoming</option><option value="NONE">No next service</option></select></label>
    </div>
    <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-5">
      {error ? <ErrorState title="Could not load services" message={error} onRetry={load} /> : records === null ? <p role="status" className="text-sm text-slate-500">Loading services...</p> : !records.length ? <p className="text-sm text-slate-600">{q || type || due ? 'No services match these filters.' : 'No service records yet. Open a saved product and choose Add service record.'}</p> : <ServiceTimeline records={records} showProduct onEdit={setEditing} onDelete={remove} busy={busy} />}
    </div>
    {editing && <ServiceRecordForm product={{documentId:editing.documentId,productId:editing.productId,position:editing.itemPosition}} record={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); toast('Service record saved.'); }} />}
  </main>;
}
