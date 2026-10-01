import { CalendarClock, Pencil, Trash, Wrench } from 'lucide-react';
import { Link } from 'react-router';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import type { ServiceRecord } from '@/types';
import { describeDueDate, formatCurrency, formatDate } from '@/utils/format';
import { SERVICE_TYPE_LABELS } from '@/utils/labels';
import { dueTone } from '@/utils/service';

interface ServiceHistoryProps {
  records: ServiceRecord[];
  showProduct?: boolean;
  onEdit?: (record: ServiceRecord) => void;
  onDelete?: (record: ServiceRecord) => void;
}

/** Service timeline: date, type, center, cost, notes and the next due date. */
export function ServiceHistory({ records, showProduct = true, onEdit, onDelete }: ServiceHistoryProps) {
  return (
    <ul className="divide-y divide-slate-100">
      {records.map((record) => (
        <li key={record.id} className="flex gap-3 px-5 py-4 sm:px-6">
          <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full bg-sky-50 text-sky-600">
            <Wrench className="size-4" aria-hidden />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1">
              <div className="min-w-0">
                <p className="text-sm font-semibold text-slate-900">
                  {SERVICE_TYPE_LABELS[record.serviceType]}
                  <span className="font-normal text-slate-500"> · {formatDate(record.serviceDate)}</span>
                </p>
                <p className="mt-0.5 text-sm text-slate-600">
                  {showProduct && (
                    <>
                      <Link to={`/products/${record.productId}`} className="font-medium text-brand-700 hover:text-brand-800">
                        {record.productName}
                      </Link>
                      {' · '}
                    </>
                  )}
                  {record.serviceCenter ?? 'Service center not recorded'}
                </p>
              </div>
              <p className="text-sm font-semibold text-slate-900">
                {record.cost === null ? <span className="font-normal text-slate-400">No cost</span> : formatCurrency(record.cost, record.currency)}
              </p>
            </div>

            {record.notes && <p className="mt-1.5 text-sm text-slate-500">{record.notes}</p>}

            <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
              {record.nextServiceDate ? (
                <Badge tone={dueTone(record.nextServiceDate)} icon={<CalendarClock className="size-3.5" aria-hidden />}>
                  Next service {formatDate(record.nextServiceDate)} · {describeDueDate(record.nextServiceDate)}
                </Badge>
              ) : (
                <span className="text-xs text-slate-400">No next service scheduled</span>
              )}
              {(onEdit || onDelete) && (
                <div className="flex gap-1">
                  {onEdit && (
                    <Button variant="ghost" size="icon-sm" aria-label="Edit service record" title="Edit" onClick={() => onEdit(record)}>
                      <Pencil className="size-4" aria-hidden />
                    </Button>
                  )}
                  {onDelete && (
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Delete service record"
                      title="Delete"
                      className="hover:bg-rose-50 hover:text-rose-600"
                      onClick={() => onDelete(record)}
                    >
                      <Trash className="size-4" aria-hidden />
                    </Button>
                  )}
                </div>
              )}
            </div>
          </div>
        </li>
      ))}
    </ul>
  );
}
