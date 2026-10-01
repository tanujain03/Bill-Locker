import { useState } from 'react';
import { WARRANTY_STATUS_STYLE } from '@/components/warranties/warranty-style';
import { cn } from '@/lib/cn';
import type { WarrantyStats, WarrantyStatus } from '@/types';
import { WARRANTY_STATUS_LABELS } from '@/utils/warranty';
import { CHART_COLORS } from './chart-tokens';

const SEGMENTS: { key: Exclude<keyof WarrantyStats, 'total'>; status: WarrantyStatus; color: string }[] = [
  { key: 'active', status: 'ACTIVE', color: CHART_COLORS.statusGood },
  { key: 'expiringSoon', status: 'EXPIRING_SOON', color: CHART_COLORS.statusWarning },
  { key: 'expired', status: 'EXPIRED', color: CHART_COLORS.statusCritical },
  { key: 'unknown', status: 'UNKNOWN', color: CHART_COLORS.statusUnknown },
];

/**
 * Part-to-whole as one stacked bar (2px surface gaps, 4px rounded ends) plus a
 * legend that doubles as the table view: icon + label + count + share.
 */
export function WarrantyStatusBar({ stats }: { stats: WarrantyStats }) {
  const [hovered, setHovered] = useState<WarrantyStatus | null>(null);
  const total = SEGMENTS.reduce((sum, segment) => sum + stats[segment.key], 0);
  const share = (count: number) => (total ? Math.round((count / total) * 100) : 0);
  const visible = SEGMENTS.filter((segment) => stats[segment.key] > 0);

  return (
    <div>
      <div className="relative">
        <div
          className="flex h-3 w-full gap-0.5 overflow-hidden rounded bg-slate-100"
          role="img"
          aria-label={`Warranty status: ${SEGMENTS.map((s) => `${WARRANTY_STATUS_LABELS[s.status]} ${stats[s.key]}`).join(', ')}`}
        >
          {visible.map((segment) => (
            <div
              key={segment.key}
              onPointerEnter={() => setHovered(segment.status)}
              onPointerLeave={() => setHovered(null)}
              className={cn('h-full transition-opacity', hovered && hovered !== segment.status && 'opacity-40')}
              style={{ flexGrow: stats[segment.key], backgroundColor: segment.color }}
            />
          ))}
        </div>
      </div>

      <ul className="mt-5 space-y-1">
        {SEGMENTS.map((segment) => {
          const { icon: Icon } = WARRANTY_STATUS_STYLE[segment.status];
          const count = stats[segment.key];
          return (
            <li
              key={segment.key}
              onPointerEnter={() => setHovered(segment.status)}
              onPointerLeave={() => setHovered(null)}
              className={cn(
                'flex items-center gap-2.5 rounded-lg px-2 py-1.5 transition-colors',
                hovered === segment.status && 'bg-slate-50',
              )}
            >
              <span className="size-2.5 shrink-0 rounded-sm" style={{ backgroundColor: segment.color }} aria-hidden />
              <Icon className="size-4 shrink-0 text-slate-500" aria-hidden />
              <span className="text-sm text-slate-600">{WARRANTY_STATUS_LABELS[segment.status]}</span>
              <span className="tabular ml-auto text-sm font-semibold text-slate-900">{count}</span>
              <span className="tabular w-10 text-right text-xs text-slate-500">{share(count)}%</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
