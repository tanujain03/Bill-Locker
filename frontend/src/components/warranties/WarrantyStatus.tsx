import { Badge } from '@/components/ui/Badge';
import { ProgressBar } from '@/components/ui/misc';
import type { WarrantyStatus, WarrantySummary } from '@/types';
import { formatDate } from '@/utils/format';
import {
  describeWarranty,
  WARRANTY_STATUS_LABELS,
  warrantyElapsedPercent,
  warrantyStatusOf,
} from '@/utils/warranty';
import { WARRANTY_STATUS_STYLE } from './warranty-style';

export function WarrantyStatusBadge({ status, size = 'md' }: { status: WarrantyStatus; size?: 'sm' | 'md' }) {
  const { tone, icon: Icon } = WARRANTY_STATUS_STYLE[status];
  return (
    <Badge tone={tone} size={size} icon={<Icon className="size-3.5" aria-hidden />}>
      {WARRANTY_STATUS_LABELS[status]}
    </Badge>
  );
}

/** "Expires in 25 days · until 23 Oct 2026" with a meter of the period already used. */
export function WarrantyMeter({ warranty }: { warranty: WarrantySummary | null }) {
  const status = warrantyStatusOf(warranty);
  const elapsed = warrantyElapsedPercent(warranty);
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 text-xs">
        <span className="font-medium text-slate-700">{describeWarranty(warranty)}</span>
        {warranty?.expiryDate && <span className="text-slate-500">until {formatDate(warranty.expiryDate)}</span>}
      </div>
      <ProgressBar
        value={elapsed ?? 0}
        tone={WARRANTY_STATUS_STYLE[status].tone}
        label={elapsed === null ? 'Warranty period unknown' : `${elapsed}% of warranty period used`}
      />
    </div>
  );
}
