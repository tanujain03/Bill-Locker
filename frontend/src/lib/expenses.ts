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
import {
  BILL_CATEGORY_LABELS,
  DOCUMENT_TYPE_LABELS,
  listDocuments,
  monthLabel,
  type BillCategory,
  type DocumentSummary,
} from './documents';

/** `budget` is the category's monthly limit, or null. */
export type CategorySpend = {
  category: BillCategory | null;
  amount: number;
  bills: number;
  previousAmount: number;
  budget: number | null;
};
export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export type WeekdaySpend = { day: Weekday; amount: number; bills: number };
/** A shop paid in at least 2 months with a similar amount; nextDate is an estimate (last + 1 month). */
export type RecurringSpend = {
  name: string;
  category: BillCategory | null;
  averageAmount: number;
  months: number;
  lastDate: string;
  nextDate: string;
};
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
  /** Monday first, all seven days. */
  weekdays: WeekdaySpend[];
  /** Payments that come back every month (6 months up to the period's end), biggest first. */
  recurring: RecurringSpend[];
};

export const getExpenses = (range: Range) => api<ExpenseReport>(`/expenses?from=${range.from}&to=${range.to}`);
export const setBudget = (amount: number | null) =>
  api<{ amount: number | null }>('/expenses/budget', { method: 'PUT', body: { amount } });
/** One category's monthly limit; null removes it. */
export const setCategoryBudget = (category: BillCategory, amount: number | null) =>
  api<{ amount: number | null }>(`/expenses/budgets/${category}`, { method: 'PUT', body: { amount } });

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

export type Period = 'this-month' | 'last-month' | '3-months' | '6-months' | 'this-year' | 'year' | 'custom';
export type Preset = Exclude<Period, 'custom'>;

/** In the order the dropdown shows them. */
export const PRESETS: Preset[] = ['this-month', 'last-month', '3-months', '6-months', 'this-year', 'year'];

export const PERIOD_LABELS: Record<Period, string> = {
  'this-month': 'This month',
  'last-month': 'Last month',
  '3-months': 'Last 3 months',
  '6-months': 'Last 6 months',
  'this-year': 'This year',
  year: 'Last 12 months', // "?period=year" (earlier links) keeps working
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
 * The dates of a ready-made period. "Last 3 / 6 / 12 months" are whole months up to today:
 * the current month and the 2, 5 or 11 before it, so a month is never cut in half.
 */
export function presetRange(period: Preset): Range {
  const now = currentMonth();
  switch (period) {
    case 'this-month':
      return monthRange(now);
    case 'last-month':
      return monthRange(addMonths(now, -1));
    case '3-months':
      return { from: `${addMonths(now, -2)}-01`, to: today() };
    case '6-months':
      return { from: `${addMonths(now, -5)}-01`, to: today() };
    case 'this-year':
      return { from: `${now.slice(0, 4)}-01-01`, to: today() };
    case 'year':
      return { from: `${addMonths(now, -11)}-01`, to: today() };
  }
}

/** The preset a range matches exactly, if any. */
export const presetOf = (range: Range): Preset | undefined =>
  PRESETS.find((p) => {
    const r = presetRange(p);
    return r.from === range.from && r.to === range.to;
  });

const addDays = (iso: string, days: number) => new Date(Date.parse(`${iso}T00:00:00Z`) + days * 86_400_000).toISOString().slice(0, 10);

/**
 * The period just before (-1) or after (+1), of the same length. A period that starts on the
 * 1st moves by whole months (1 May – 13 Oct → 1 Nov – 30 Apr); any other moves by its days.
 * A period may not end after today.
 */
export function shiftRange(range: Range, direction: -1 | 1): Range {
  let next: Range;
  if (range.from.endsWith('-01')) {
    const months = monthsTouched(range);
    const fromMonth = addMonths(range.from.slice(0, 7), direction * months);
    next = { from: `${fromMonth}-01`, to: monthRange(addMonths(fromMonth, months - 1)).to };
  } else {
    const days = daysBetween(range.from, range.to);
    next = { from: addDays(range.from, direction * days), to: addDays(range.to, direction * days) };
  }
  return next.to > today() ? { from: next.from, to: today() } : next;
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

/**
 * How many months of budget a period is worth: the calendar months it covers when it starts
 * on the 1st (Last 6 months = 6), else its days ÷ 30.44 (one decimal).
 */
export function periodMonths(range: Range): number {
  return range.from.endsWith('-01') ? monthsTouched(range) : Math.round((daysBetween(range.from, range.to) / 30.44) * 10) / 10;
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

/**
 * Downloads the saved bills of a period as a CSV file (opens in Excel or Google Sheets).
 * Uses the Documents list, filtered here like the Documents page does.
 */
export async function exportCsv(range: Range): Promise<number> {
  const bills = (await listDocuments({ status: 'SAVED' }))
    .filter((d) => d.purchaseDate !== null && d.purchaseDate >= range.from && d.purchaseDate <= range.to)
    .sort((a, b) => (a.purchaseDate ?? '').localeCompare(b.purchaseDate ?? ''));
  const rows = [
    ['Date', 'Shop', 'Category', 'Type', 'Products', 'Amount (INR)', 'Bill number'],
    ...bills.map((d) => [
      d.purchaseDate ?? '',
      d.sellerName ?? d.fileName,
      d.category ? BILL_CATEGORY_LABELS[d.category] : '',
      d.documentType ? DOCUMENT_TYPE_LABELS[d.documentType] : '',
      d.firstProductName ? (d.itemCount > 1 ? `${d.firstProductName} +${d.itemCount - 1} more` : d.firstProductName) : '',
      d.totalAmount === null ? '' : d.totalAmount.toFixed(2),
      d.documentNumber ?? '',
    ]),
  ];
  // "\uFEFF" makes Excel read the file as UTF-8 (₹, accents).
  const csv = '\uFEFF' + rows.map((r) => r.map(csvCell).join(',')).join('\r\n');
  const link = document.createElement('a');
  link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  link.download = `bill-locker-expenses_${range.from}_to_${range.to}.csv`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000); // after the browser has started the download
  return bills.length;
}

/**
 * One CSV value: quoted, inner quotes doubled. Text read from a bill that starts with = + - @
 * gets a leading apostrophe, so a spreadsheet shows it instead of running it as a formula.
 */
function csvCell(value: string): string {
  const safe = /^[=+\-@]/.test(value) && !/^-?\d+(\.\d+)?$/.test(value) ? `'${value}` : value;
  return `"${safe.replace(/"/g, '""')}"`;
}

/** "+23%" / "−8%" against the period before; null when there is nothing to compare with. */
export function change(now: number, before: number): number | null {
  if (before <= 0) return null;
  return Math.round(((now - before) / before) * 100);
}
