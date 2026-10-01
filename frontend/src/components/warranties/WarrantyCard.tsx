import { Pencil } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ProgressBar } from '@/components/ui/misc';
import { cn } from '@/lib/cn';
import type { Product, WarrantyStatus } from '@/types';
import { formatDate } from '@/utils/format';
import {
  describeWarranty,
  formatWarrantyPeriod,
  warrantyElapsedPercent,
  warrantyStatusOf,
} from '@/utils/warranty';
import { WARRANTY_STATUS_STYLE } from './warranty-style';

const HEADLINES: Record<WarrantyStatus, string> = {
  ACTIVE: 'Warranty active',
  EXPIRING_SOON: 'Warranty expiring soon',
  EXPIRED: 'Warranty expired',
  UNKNOWN: 'Warranty unknown',
};

const PANEL: Record<WarrantyStatus, string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-700',
  EXPIRING_SOON: 'bg-amber-50 text-amber-700',
  EXPIRED: 'bg-rose-50 text-rose-700',
  UNKNOWN: 'bg-slate-100 text-slate-600',
};

/** Product page warranty summary: status, days remaining, expiry and coverage. */
export function WarrantyCard({ product, onEdit }: { product: Product; onEdit: () => void }) {
  const warranty = product.warranty;
  const status = warrantyStatusOf(warranty);
  const { icon: Icon, tone } = WARRANTY_STATUS_STYLE[status];
  const elapsed = warrantyElapsedPercent(warranty);

  return (
    <Card className="overflow-hidden">
      <div className={cn('flex items-center gap-3 px-5 py-4 sm:px-6', PANEL[status])}>
        <Icon className="size-6 shrink-0" aria-hidden />
        <div>
          <p className="font-semibold">{HEADLINES[status]}</p>
          <p className="text-sm opacity-90">{describeWarranty(warranty)}</p>
        </div>
      </div>

      {status === 'UNKNOWN' ? (
        <div className="px-5 py-5 sm:px-6">
          <p className="text-sm text-slate-600">
            No warranty period was found on this product’s documents, so we can’t calculate an expiry date yet.
          </p>
          <Button variant="secondary" size="sm" className="mt-4" onClick={onEdit} leftIcon={<Pencil className="size-3.5" aria-hidden />}>
            Add warranty period
          </Button>
        </div>
      ) : (
        <div className="space-y-4 px-5 py-5 sm:px-6">
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="text-xs text-slate-500">Expiry date</dt>
              <dd className="mt-0.5 font-semibold text-slate-900">{formatDate(warranty?.expiryDate)}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Coverage</dt>
              <dd className="mt-0.5 font-semibold text-slate-900">{formatWarrantyPeriod(warranty?.warrantyMonths)}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Starts</dt>
              <dd className="mt-0.5 font-medium text-slate-700">{formatDate(warranty?.startDate)}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Days remaining</dt>
              <dd className="mt-0.5 font-medium text-slate-700">
                {warranty?.daysRemaining !== null && warranty?.daysRemaining !== undefined
                  ? Math.max(0, warranty.daysRemaining).toLocaleString('en-IN')
                  : '—'}
              </dd>
            </div>
          </dl>
          {elapsed !== null && (
            <div>
              <ProgressBar value={elapsed} tone={tone} label={`${elapsed}% of the warranty period used`} />
              <p className="mt-1.5 text-xs text-slate-500">{elapsed}% of the warranty period used</p>
            </div>
          )}
          <p className="text-xs text-slate-400">Expiry is calculated from the purchase date — never guessed by AI.</p>
        </div>
      )}
    </Card>
  );
}
