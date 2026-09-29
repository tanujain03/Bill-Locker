import type { ISODate } from '@/types';

const MS_PER_DAY = 86_400_000;

/**
 * Parses `YYYY-MM-DD` as a *local* calendar date. `new Date('2026-09-15')`
 * would be UTC midnight and shift a day in negative-offset time zones.
 */
export function parseISODate(value: ISODate): Date {
  const [year, month, day] = value.slice(0, 10).split('-').map(Number);
  return new Date(year, (month ?? 1) - 1, day ?? 1);
}

export function toISODate(date: Date): ISODate {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function todayISO(): ISODate {
  return toISODate(new Date());
}

export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

/** Whole calendar days from `from` (default today) to `date`; negative when in the past. */
export function daysUntil(date: ISODate, from: Date = new Date()): number {
  const target = parseISODate(date).getTime();
  const origin = startOfDay(from).getTime();
  return Math.round((target - origin) / MS_PER_DAY);
}

/** Adds calendar months, clamping to the month's last day (Java `LocalDate.plusMonths`). */
export function addMonths(date: Date, months: number): Date {
  const targetMonthIndex = date.getMonth() + months;
  const firstOfTarget = new Date(date.getFullYear(), targetMonthIndex, 1);
  const lastDay = new Date(firstOfTarget.getFullYear(), firstOfTarget.getMonth() + 1, 0).getDate();
  return new Date(firstOfTarget.getFullYear(), firstOfTarget.getMonth(), Math.min(date.getDate(), lastDay));
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/**
 * Warranty expiry = start + months - 1 day (a 24-month warranty bought on
 * 2026-09-15 covers up to and including 2028-09-14). The backend owns this
 * rule; the UI uses it only to preview the result while the user edits.
 */
export function calculateWarrantyExpiry(startDate: ISODate, warrantyMonths: number): ISODate {
  return toISODate(addDays(addMonths(parseISODate(startDate), warrantyMonths), -1));
}

export function isValidISODate(value: string | null | undefined): value is ISODate {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = parseISODate(value);
  return toISODate(parsed) === value;
}
