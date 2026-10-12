import { CalendarRange, Wallet } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { TopShops } from '../components/dashboard/TopShops';
import { CategoryBreakdown } from '../components/expenses/CategoryBreakdown';
import { DailyCalendar } from '../components/expenses/DailyCalendar';
import { Insights } from '../components/expenses/Insights';
import { MonthTrend } from '../components/expenses/MonthTrend';
import { SpendHero } from '../components/expenses/SpendHero';
import { TopProducts } from '../components/expenses/TopProducts';
import { Button, buttonClass } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { UploadButton } from '../components/UploadButton';
import { errorMessage } from '../lib/api';
import { monthLabel } from '../lib/documents';
import {
  expenseLink,
  getExpenses,
  monthRange,
  PERIOD_LABELS,
  presetRange,
  rangeLabel,
  today,
  type ExpenseReport,
  type Period,
  type Range,
} from '../lib/expenses';
import { usePageTitle } from '../lib/usePageTitle';

const PRESETS = ['this-month', 'last-month', '6-months', 'year'] as const;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * /expenses: where the money went in a period — this month, last month, the last 6 months,
 * the last year or a custom range. The backend adds it all up (GET /api/expenses?from=&to=);
 * the period lives in the URL (?period=6-months, or ?period=custom&from=…&to=…), so Back
 * and refresh keep it.
 */
export function ExpensesPage() {
  usePageTitle('Expenses');
  const [params, setParams] = useSearchParams();
  const { period, range } = readPeriod(params);
  const [report, setReport] = useState<ExpenseReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const latest = useRef(0); // only the newest answer may update the page

  const load = useCallback(() => {
    const mine = ++latest.current;
    setError(null);
    getExpenses({ from: range.from, to: range.to })
      .then((r) => mine === latest.current && setReport(r))
      .catch((e) => mine === latest.current && setError(errorMessage(e)));
  }, [range.from, range.to]);

  useEffect(load, [load]);

  /** A preset, or any range (a range that equals a preset shows as that preset). */
  const choose = (next: Period | Range) => {
    if (typeof next === 'string') {
      setParams(next === 'this-month' ? {} : { period: next });
      return;
    }
    const preset = PRESETS.find((p) => {
      const r = presetRange(p);
      return r.from === next.from && r.to === next.to;
    });
    setParams(preset ? (preset === 'this-month' ? {} : { period: preset }) : { period: 'custom', ...next });
  };
  // The shown report may still be the previous period's while the new one loads.
  const shown = report && report.from === range.from && report.to === range.to ? report : null;

  return (
    <main className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-2xl font-semibold tracking-tight">Expenses</h1>
      <p className="mt-1 text-slate-600">
        Where your money went, from the bills you saved · <span className="font-medium text-slate-800">{rangeLabel(range)}</span>
      </p>

      <PeriodPicker period={period} range={range} onChoose={choose} />

      <div className="mt-6">
        {error ? (
          <ErrorState title="Could not load your expenses" message={error} onRetry={load} />
        ) : !shown ? (
          <Skeleton />
        ) : (
          <div className="space-y-6">
            <SpendHero report={shown} onChanged={load} />
            {shown.bills === 0 ? (
              <EmptyPeriod report={shown} onChoose={choose} />
            ) : (
              <div className="grid gap-6 lg:grid-cols-2">
                <CategoryBreakdown report={shown} />
                <Insights report={shown} />
                {shown.days.length > 0 && <DailyCalendar days={shown.days} />}
                <MonthTrend months={shown.months} range={range} onSelect={(month) => choose(monthRange(month))} />
                <TopProducts products={shown.topProducts} />
                <TopShops shops={shown.topShops} />
              </div>
            )}
            {shown.bills > 0 && (
              <p className="text-center text-sm">
                <Link to={expenseLink(range)} className="font-medium text-brand-700 hover:text-brand-800">
                  See all {shown.bills} bills of {rangeLabel(range)}
                </Link>
              </p>
            )}
          </div>
        )}
      </div>
    </main>
  );
}

/** The period from the URL; anything missing or malformed means "This month". */
function readPeriod(params: URLSearchParams): { period: Period; range: Range } {
  const period = params.get('period');
  if (period === 'custom') {
    const from = params.get('from') ?? '';
    const to = params.get('to') ?? '';
    if (DATE.test(from) && DATE.test(to)) return { period: 'custom', range: { from, to } };
  }
  const preset = PRESETS.find((p) => p === period) ?? 'this-month';
  return { period: preset, range: presetRange(preset) };
}

/** The period buttons, and the two dates for a custom range. */
function PeriodPicker({ period, range, onChoose }: { period: Period; range: Range; onChoose: (p: Period | Range) => void }) {
  const [customOpen, setCustomOpen] = useState(period === 'custom');
  const [from, setFrom] = useState(range.from);
  const [to, setTo] = useState(range.to);
  const showCustom = customOpen || period === 'custom';

  // When the period changes (a month bar, Back, Show), the dates follow it, and the
  // custom form stays open only for a custom range.
  useEffect(() => {
    setFrom(range.from);
    setTo(range.to);
    setCustomOpen(period === 'custom');
  }, [range.from, range.to, period]);

  function apply(e: FormEvent) {
    e.preventDefault();
    if (from && to) onChoose({ from, to }); // the backend checks the order and the length
  }

  const chip = (active: boolean) =>
    `rounded-full px-3.5 py-1.5 text-sm font-medium ring-1 transition ${
      active ? 'bg-brand-600 text-white ring-brand-600' : 'bg-white text-slate-700 ring-slate-200 hover:bg-slate-50'
    }`;

  return (
    <div className="mt-6">
      <div className="flex flex-wrap gap-2" role="group" aria-label="Period">
        {PRESETS.map((p) => (
          <button
            key={p}
            type="button"
            aria-pressed={period === p}
            onClick={() => {
              setCustomOpen(false);
              onChoose(p);
            }}
            className={chip(period === p && !customOpen)}
          >
            {PERIOD_LABELS[p]}
          </button>
        ))}
        <button
          type="button"
          aria-pressed={showCustom}
          aria-expanded={showCustom}
          onClick={() => setCustomOpen(true)}
          className={`inline-flex items-center gap-1.5 ${chip(showCustom)}`}
        >
          <CalendarRange className="size-4" aria-hidden />
          {PERIOD_LABELS.custom}
        </button>
      </div>

      {showCustom && (
        <form
          onSubmit={apply}
          noValidate
          className="mt-3 flex flex-wrap items-end gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-xs"
        >
          <DateInput label="From" value={from} onChange={setFrom} />
          <DateInput label="To" value={to} onChange={setTo} />
          <Button type="submit" variant="primary">
            Show
          </Button>
          <p className="w-full text-xs text-slate-500">Up to 3 years. Both days are included.</p>
        </form>
      )}
    </div>
  );
}

function DateInput({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
      {label}
      <input
        type="date"
        value={value}
        max={today()}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
      />
    </label>
  );
}

/** A period without saved bills: say so, and offer the latest month that has some. */
function EmptyPeriod({ report, onChoose }: { report: ExpenseReport; onChoose: (r: Range) => void }) {
  const latest = [...report.months].reverse().find((m) => m.amount > 0);
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-14 text-center">
      <span className="grid size-14 place-items-center rounded-2xl bg-brand-50 text-brand-600">
        <Wallet className="size-7" aria-hidden />
      </span>
      <h2 className="mt-4 text-lg font-semibold">No saved bills in {rangeLabel(report)}</h2>
      <p className="mt-1 max-w-md text-sm text-slate-600">
        Upload a bill or receipt with a date and a total, and this page shows where the money went: by category, product,
        shop and day.
      </p>
      <div className="mt-6 flex flex-wrap justify-center gap-2">
        <UploadButton>Upload a bill</UploadButton>
        {latest && (
          <button type="button" onClick={() => onChoose(monthRange(latest.month))} className={buttonClass()}>
            Go to {monthLabel(latest.month)}
          </button>
        )}
      </div>
    </div>
  );
}

/** Grey blocks in the page's shape while the numbers load. */
function Skeleton() {
  return (
    <div className="animate-pulse space-y-6" aria-label="Loading expenses">
      <div className="h-64 rounded-3xl bg-slate-200" />
      <div className="grid gap-6 lg:grid-cols-2">
        {[1, 2, 3, 4].map((n) => (
          <div key={n} className="h-64 rounded-2xl bg-slate-200" />
        ))}
      </div>
    </div>
  );
}
