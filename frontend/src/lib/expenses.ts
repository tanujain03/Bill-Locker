/**
 * The Expenses page: the backend adds up the spending of one period (ExpenseService);
 * this file holds the shape of that answer, the calls, the periods, and how each
 * category looks.
 */
import {
  Clapperboard,
  Fuel,
  HeartPulse,
  House,
  Laptop,
  Package,
  Plane,
  Refrigerator,
  Shirt,
  ShoppingBag,
  ShoppingBasket,
  Smartphone,
  Tag,
  UtensilsCrossed,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import { api } from './api';
import { monthLabel, type BillCategory, type DocumentSummary } from './documents';

export type CategorySpend = { category: BillCategory | null; amount: number; bills: number; previousAmount: number };
export type ProductSpend = { name: string; category: BillCategory | null; amount: number; seller: string | null; documentId: string };
export type ShopSpend = { name: string; amount: number; bills: number };
export type DaySpend = { date: string; amount: number; bills: number };
export type MonthSpend = { month: string; amount: number; bills: number };

export type ExpenseReport = {
  /** The period, "YYYY-MM-DD", both days included. */
  from: string;
  to: string;
  /** The period just before, to compare with (same number of whole months, or of days). */
  previousFrom: string;
  previousTo: string;
  total: number;
  bills: number;
  previousTotal: number;
  dailyAverage: number;
  /** Only while the period runs (e.g. this month): the total at this pace by its end. */
  projectedTotal: number | null;
  budget: number | null;
  /** Biggest first; category null = bills nobody sorted yet. */
  categories: CategorySpend[];
  topProducts: ProductSpend[];
  topShops: ShopSpend[];
  /** Every day of the period; empty when it is longer than 62 days. */
  days: DaySpend[];
  /** Months up to the period's last one, oldest first (at least 12). */
  months: MonthSpend[];
  biggestBill: DocumentSummary | null;
  uncategorizedBills: number;
};

export const getExpenses = (range: Range) => api<ExpenseReport>(`/expenses?from=${range.from}&to=${range.to}`);
export const setBudget = (amount: number | null) =>
  api<{ amount: number | null }>('/expenses/budget', { method: 'PUT', body: { amount } });

/** Each category's icon: a category is never shown by colour alone. */
export const CATEGORY_ICONS: Record<BillCategory, LucideIcon> = {
  TRAVEL: Plane,
  FOOD: UtensilsCrossed,
  GROCERIES: ShoppingBasket,
  FUEL: Fuel,
  UTILITIES: Zap,
  PHONE_INTERNET: Smartphone,
  SHOPPING: ShoppingBag,
  HEALTH: HeartPulse,
  ELECTRONICS: Laptop,
  APPLIANCES: Refrigerator,
  FASHION: Shirt,
  HOME: House,
  ENTERTAINMENT: Clapperboard,
  OTHER: Package,
};

export const categoryIcon = (category: BillCategory | null) => (category ? CATEGORY_ICONS[category] : Tag);

// ---- Periods ----------------------------------------------------------------

/** A period: "YYYY-MM-DD", both days included. */
export type Range = { from: string; to: string };

export type Period = 'this-month' | 'last-month' | '6-months' | 'year' | 'custom';

export const PERIOD_LABELS: Record<Period, string> = {
  'this-month': 'This month',
  'last-month': 'Last month',
  '6-months': 'Last 6 months',
  year: 'Last year',
  custom: 'Custom range',
};

/** "2026-10" plus/minus some months, as "YYYY-MM". */
export function addMonths(month: string, delta: number): string {
  const [year, m] = month.split('-').map(Number);
  const date = new Date(Date.UTC(year, m - 1 + delta, 1));
  return date.toISOString().slice(0, 7);
}

/** One whole calendar month, "2026-09" → 1 Sep … 30 Sep. */
export function monthRange(month: string): Range {
  const [year, m] = month.split('-').map(Number);
  const last = new Date(Date.UTC(year, m, 0)).getUTCDate(); // day 0 of next month = last day of this one
  return { from: `${month}-01`, to: `${month}-${String(last).padStart(2, '0')}` };
}

/**
 * The dates of a ready-made period. "Last 6 months" and "Last year" are whole months up to
 * today: the current month and the 5 (or 11) before it, so a month is never cut in half.
 */
export function presetRange(period: Exclude<Period, 'custom'>): Range {
  const now = currentMonth();
  switch (period) {
    case 'this-month':
      return monthRange(now);
    case 'last-month':
      return monthRange(addMonths(now, -1));
    case '6-months':
      return { from: `${addMonths(now, -5)}-01`, to: today() };
    case 'year':
      return { from: `${addMonths(now, -11)}-01`, to: today() };
  }
}

/** When the period is exactly one calendar month, that month ("YYYY-MM"); else null. */
export function singleMonth(range: Range): string | null {
  const month = range.from.slice(0, 7);
  const whole = monthRange(month);
  return range.from === whole.from && range.to === whole.to ? month : null;
}

const day = (iso: string, withYear: boolean) =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: withYear ? 'numeric' : undefined,
    timeZone: 'UTC',
  });

/** "Oct 2026" for one month, else "1 May – 7 Oct 2026" (the year twice only when it differs). */
export function rangeLabel(range: Range): string {
  const month = singleMonth(range);
  if (month) return monthLabel(month);
  if (range.from === range.to) return day(range.from, true);
  const sameYear = range.from.slice(0, 4) === range.to.slice(0, 4);
  return `${day(range.from, !sameYear)} – ${day(range.to, true)}`;
}

/** What the period is compared with, for sentences: "Sept", "the 6 months before", "the period before". */
export function previousLabel(r: ExpenseReport): string {
  const previous = { from: r.previousFrom, to: r.previousTo };
  const month = singleMonth(previous);
  if (month) return monthLabel(month).split(' ')[0]; // one month before one month
  // Whole months (the backend compares those month for month): "the 3 months before".
  if (r.previousFrom.endsWith('-01') && r.previousTo === monthRange(r.previousTo.slice(0, 7)).to) {
    return `the ${monthsTouched(previous)} months before`;
  }
  return 'the period before';
}

/** How many days a period has, both ends included. */
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1;
}

/** How many calendar months a period touches (1 May – 7 Oct = 6). */
export function monthsTouched(range: Range): number {
  const [fy, fm] = range.from.split('-').map(Number);
  const [ty, tm] = range.to.split('-').map(Number);
  return (ty - fy) * 12 + (tm - fm) + 1;
}

/** Today in the browser's time zone, "YYYY-MM-DD" (toISOString would give the UTC date). */
export function today(): string {
  const now = new Date();
  const two = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${two(now.getMonth() + 1)}-${two(now.getDate())}`;
}

/** The current month in the browser's time zone, "YYYY-MM". */
export const currentMonth = () => today().slice(0, 7);

/** The saved bills of a period (and one category; 'NONE' = not sorted yet) on the Documents page. */
export const expenseLink = (range: Range, category?: BillCategory | null) =>
  `/documents?status=SAVED&from=${range.from}&to=${range.to}${category === undefined ? '' : `&category=${category ?? 'NONE'}`}`;

/** "+23%" / "−8%" against the period before; null when there is nothing to compare with. */
export function change(now: number, before: number): number | null {
  if (before <= 0) return null;
  return Math.round(((now - before) / before) * 100);
}
