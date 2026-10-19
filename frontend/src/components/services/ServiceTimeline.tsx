import { CalendarClock, CircleAlert, CircleCheck, Clock, PencilLine, Trash2, Wrench } from 'lucide-react';
import { Link } from 'react-router';
import { formatDate } from '../../lib/document-form';
import { formatAmount } from '../../lib/documents';
import { productLink } from '../../lib/products';
import { DUE_LABELS, SERVICE_TYPES, type ServiceRecord } from '../../lib/service-records';
import { Button } from '../Button';

export function ServiceDueBadge({ record: r }: { record: ServiceRecord }) {
  const Icon = r.dueStatus === 'OVERDUE' ? CircleAlert : r.dueStatus === 'DUE_SOON' ? CalendarClock : Clock;
  return <span className={`inline-flex items-center gap-1 text-xs font-medium ${r.dueStatus === 'OVERDUE' ? 'text-rose-700' : r.dueStatus === 'DUE_SOON' ? 'text-amber-700' : 'text-slate-600'}`}><Icon className="size-3.5" aria-hidden />{DUE_LABELS[r.dueStatus]}</span>;
}

export function ServiceTimeline({ records, showProduct = false, onEdit, onDelete, busy = false }: {
  records: ServiceRecord[]; showProduct?: boolean; onEdit?: (r: ServiceRecord) => void;
  onDelete?: (r: ServiceRecord) => void; busy?: boolean;
}) {
  if (!records.length) return <p className="text-sm text-slate-500">No service records yet.</p>;
  return <ol className="divide-y divide-slate-100">
    {records.map((r) => { const ClaimIcon = r.claimStatus === 'RESOLVED' ? CircleCheck : r.claimStatus === 'REJECTED' ? CircleAlert : Clock; return <li key={r.id} className="flex gap-3 py-4 first:pt-0">
      <span className="mt-1 grid size-8 shrink-0 place-items-center rounded-full bg-brand-50 text-brand-600"><Wrench className="size-4" aria-hidden /></span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2"><time dateTime={r.serviceDate} className="text-sm text-slate-600">{formatDate(r.serviceDate)}</time><span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">{SERVICE_TYPES[r.type]}</span></div>
          {(onEdit || onDelete) && <div className="flex gap-1 print:hidden">
            {onEdit && <Button size="sm" variant="ghost" icon={PencilLine} disabled={busy} onClick={() => onEdit(r)} aria-label={`Edit ${r.title}`} />}
            {onDelete && <Button size="sm" variant="dangerGhost" icon={Trash2} disabled={busy} onClick={() => onDelete(r)} aria-label={`Delete ${r.title}`} />}
          </div>}
        </div>
        <h3 className="mt-1 font-medium break-words">{r.title}</h3>
        {showProduct && <Link to={productLink(r.productId)} className="text-sm break-words text-brand-700 hover:underline">{r.productName ?? 'Product'}</Link>}
        <p className="mt-1 text-sm text-slate-600">{[r.providerName, r.cost === null ? 'Cost not recorded' : formatAmount(r.cost), r.coveredByWarranty ? 'Covered by warranty' : null].filter(Boolean).join(' · ')}</p>
        {r.providerContact && <p className="text-sm break-words text-slate-600">{r.providerContact}</p>}
        {r.type === 'WARRANTY_CLAIM' && <p className="mt-1 text-sm text-slate-700"><ClaimIcon className="mr-1 inline size-3.5" aria-hidden />Claim {r.claimNumber ?? '(number not recorded)'}{r.claimStatus ? ` · ${r.claimStatus === 'OPEN' ? 'Open' : r.claimStatus === 'RESOLVED' ? 'Resolved' : 'Rejected'}` : ''}</p>}
        {r.description && <p className="mt-2 text-sm whitespace-pre-wrap break-words text-slate-600">{r.description}</p>}
        {r.nextDueDate && <div className="mt-2 flex flex-wrap items-center gap-2"><ServiceDueBadge record={r} /><span className="text-xs text-slate-500">{formatDate(r.nextDueDate)}{!r.reminderActive ? ' · Previous schedule' : ''}{r.repeatMonths ? ` · Every ${r.repeatMonths} month(s)` : ''}</span></div>}
        {r.attachmentDocumentId && <Link to={`/documents/${r.attachmentDocumentId}`} className="mt-2 inline-block text-sm text-brand-700 hover:underline">Open attached document</Link>}
      </div>
    </li>; })}
  </ol>;
}
