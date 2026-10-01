import type { WarrantyStatus, WarrantySummary } from '@/types';
import { daysUntil, parseISODate } from './date';

export const WARRANTY_STATUS_LABELS: Record<WarrantyStatus, string> = {
  ACTIVE: 'Active',
  EXPIRING_SOON: 'Expiring soon',
  EXPIRED: 'Expired',
  UNKNOWN: 'Unknown',
};

/** Normalises a missing warranty to UNKNOWN so the UI has one code path. */
export function warrantyStatusOf(warranty: WarrantySummary | null | undefined): WarrantyStatus {
  return warranty?.status ?? 'UNKNOWN';
}

/** Days remaining, preferring the server value and falling back to the expiry date. */
export function warrantyDaysRemaining(warranty: WarrantySummary | null | undefined): number | null {
  if (!warranty) return null;
  if (typeof warranty.daysRemaining === 'number') return warranty.daysRemaining;
  return warranty.expiryDate ? daysUntil(warranty.expiryDate) : null;
}

/** "Expires in 421 days", "Expires today", "Expired 12 days ago", "No warranty info". */
export function describeWarranty(warranty: WarrantySummary | null | undefined): string {
  const status = warrantyStatusOf(warranty);
  const days = warrantyDaysRemaining(warranty);
  if (status === 'UNKNOWN' || days === null) return 'Warranty period not found';
  if (days === 0) return 'Expires today';
  if (days === 1) return 'Expires tomorrow';
  if (days > 1) return `Expires in ${days.toLocaleString('en-IN')} days`;
  if (days === -1) return 'Expired yesterday';
  return `Expired ${Math.abs(days).toLocaleString('en-IN')} days ago`;
}

/** Share of the warranty period already used, 0..100 (null when unknown). */
export function warrantyElapsedPercent(
  warranty: WarrantySummary | null | undefined,
  now: Date = new Date(),
): number | null {
  if (!warranty?.startDate || !warranty.expiryDate) return null;
  const start = parseISODate(warranty.startDate).getTime();
  const end = parseISODate(warranty.expiryDate).getTime();
  if (end <= start) return 100;
  const percent = ((now.getTime() - start) / (end - start)) * 100;
  return Math.min(100, Math.max(0, Math.round(percent)));
}

export function formatWarrantyPeriod(months: number | null | undefined): string {
  if (!months) return '—';
  if (months % 12 === 0) {
    const years = months / 12;
    return `${years} ${years === 1 ? 'year' : 'years'}`;
  }
  return `${months} ${months === 1 ? 'month' : 'months'}`;
}
