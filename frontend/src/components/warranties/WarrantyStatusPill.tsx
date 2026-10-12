import { ClockAlert, ShieldCheck, ShieldQuestionMark, ShieldX, type LucideIcon } from 'lucide-react';
import type { WarrantyStatus } from '../../lib/warranties';

/** Colours for each status; also used by the dashboard's warranty bar. */
export const WARRANTY_STYLE: Record<WarrantyStatus, { Icon: LucideIcon; pill: string; bar: string; dot: string }> = {
  ACTIVE: { Icon: ShieldCheck, pill: 'bg-emerald-50 text-emerald-800 ring-emerald-200', bar: 'bg-emerald-500', dot: 'bg-emerald-500' },
  EXPIRING_SOON: { Icon: ClockAlert, pill: 'bg-amber-50 text-amber-800 ring-amber-200', bar: 'bg-amber-400', dot: 'bg-amber-400' },
  EXPIRED: { Icon: ShieldX, pill: 'bg-rose-50 text-rose-800 ring-rose-200', bar: 'bg-rose-400', dot: 'bg-rose-400' },
  NO_INFO: { Icon: ShieldQuestionMark, pill: 'bg-slate-100 text-slate-700 ring-slate-200', bar: 'bg-slate-300', dot: 'bg-slate-300' },
};

/** "12 days left", "Ends today", "Expired 3 days ago", "No end date". */
export function warrantyText(status: WarrantyStatus, daysLeft: number | null): string {
  if (status === 'NO_INFO' || daysLeft === null) return 'No end date';
  if (daysLeft === 0) return 'Ends today';
  if (daysLeft > 0) return daysLeft === 1 ? '1 day left' : `${daysLeft} days left`;
  if (daysLeft === -1) return 'Expired yesterday';
  return `Expired ${-daysLeft} days ago`;
}

/** A warranty's status: colour, icon and words together (never colour alone). */
export function WarrantyStatusPill({ status, daysLeft }: { status: WarrantyStatus; daysLeft: number | null }) {
  const { Icon, pill } = WARRANTY_STYLE[status];
  return (
    <span className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ${pill}`}>
      <Icon className="size-3.5" aria-hidden />
      {warrantyText(status, daysLeft)}
    </span>
  );
}
