import type { ISODate, ISODateTime } from '@/types';
import { daysUntil, parseISODate } from './date';

const LOCALE = 'en-IN';

const currencyFormatters = new Map<string, Intl.NumberFormat>();

function currencyFormatter(currency: string, fractionDigits: number): Intl.NumberFormat {
  const key = `${currency}:${fractionDigits}`;
  let formatter = currencyFormatters.get(key);
  if (!formatter) {
    try {
      formatter = new Intl.NumberFormat(LOCALE, {
        style: 'currency',
        currency,
        minimumFractionDigits: fractionDigits,
        maximumFractionDigits: fractionDigits,
      });
    } catch {
      // Unknown currency code: fall back to INR formatting rather than crashing.
      formatter = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: 'INR' });
    }
    currencyFormatters.set(key, formatter);
  }
  return formatter;
}

/** ₹62,990 · ₹1,499.50 · "—" when unknown. */
export function formatCurrency(amount: number | null | undefined, currency = 'INR'): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) return '—';
  const fractionDigits = Number.isInteger(amount) ? 0 : 2;
  return currencyFormatter(currency || 'INR', fractionDigits).format(amount);
}

/** Compact amounts for tight spaces: ₹3.1L, ₹42K. */
export function formatCompactCurrency(amount: number, currency = 'INR'): string {
  try {
    return new Intl.NumberFormat(LOCALE, {
      style: 'currency',
      currency,
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(amount);
  } catch {
    return formatCurrency(amount, currency);
  }
}

const dateFormatter = new Intl.DateTimeFormat(LOCALE, { day: 'numeric', month: 'short', year: 'numeric' });
const dateTimeFormatter = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});

/** 15 Jun 2026 · "—" when unknown. Accepts dates and date-times. */
export function formatDate(value: ISODate | ISODateTime | null | undefined): string {
  if (!value) return '—';
  const date = value.length <= 10 ? parseISODate(value) : new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateFormatter.format(date);
}

export function formatDateTime(value: ISODateTime | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormatter.format(date);
}

const relativeFormatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 31_536_000],
  ['month', 2_592_000],
  ['week', 604_800],
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
];

/** "3 hours ago", "yesterday", "just now". */
export function formatRelativeTime(value: ISODateTime | null | undefined, now: Date = new Date()): string {
  if (!value) return '—';
  const seconds = (new Date(value).getTime() - now.getTime()) / 1000;
  if (Number.isNaN(seconds)) return '—';
  if (Math.abs(seconds) < 45) return 'just now';
  for (const [unit, unitSeconds] of RELATIVE_UNITS) {
    if (Math.abs(seconds) >= unitSeconds) {
      return relativeFormatter.format(Math.round(seconds / unitSeconds), unit);
    }
  }
  return relativeFormatter.format(Math.round(seconds / 60), 'minute');
}

/** "in 7 days", "today", "3 days overdue" for an upcoming date such as a service. */
export function describeDueDate(date: ISODate, from: Date = new Date()): string {
  const days = daysUntil(date, from);
  if (days === 0) return 'Due today';
  if (days === 1) return 'Due tomorrow';
  if (days > 1) return `Due in ${days} days`;
  if (days === -1) return '1 day overdue';
  return `${Math.abs(days)} days overdue`;
}

export function formatFileSize(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || bytes < 0) return '—';
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${Math.round(kb)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}

export function initials(name: string | null | undefined): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const letters = parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : (parts[0]?.slice(0, 2) ?? '?');
  return letters.toUpperCase();
}

/** Morning / afternoon / evening greeting for the dashboard header. */
export function greeting(now: Date = new Date()): string {
  const hour = now.getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}
