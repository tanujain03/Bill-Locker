import { Download, Wallet } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { TopShops } from '../components/dashboard/TopShops';
import { CategoryBreakdown } from '../components/expenses/CategoryBreakdown';
import { DailyCalendar } from '../components/expenses/DailyCalendar';
import { Insights } from '../components/expenses/Insights';
import { MonthTrend } from '../components/expenses/MonthTrend';
import { PeriodMenu } from '../components/expenses/PeriodMenu';
import { RecurringPayments } from '../components/expenses/RecurringPayments';
import { SpendHero } from '../components/expenses/SpendHero';
import { TopProducts } from '../components/expenses/TopProducts';
import { WeekdayChart } from '../components/expenses/WeekdayChart';
import { Button, buttonClass } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { UploadButton } from '../components/UploadButton';
import { errorMessage } from '../lib/api';
import { monthLabel } from '../lib/documents';
import {
  expenseLink,
  exportCsv,
  getExpenses,
  monthRange,
  PRESETS,
  presetOf,
  presetRange,
  rangeLabel,
  type ExpenseReport,
  type Period,
  type Preset,
  type Range,
} from '../lib/expenses';
import { useFeedback } from '../lib/feedback-context';
import { usePageTitle } from '../lib/usePageTitle';

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * /expenses: where the money went in a period, picked from a dropdown (this month, last
 * month, the last 3, 6 or 12 months, this year, or a custom range) and stepped with ‹ ›.
 * The backend adds it all up (GET /api/expenses?from=&to=); the period lives in the URL
 * (?period=6-months, or ?period=custom&from=…&to=…), so Back and refresh keep it.
 */
export function ExpensesPage() {
  usePageTitle('Expenses');
  const { toast } = useFeedback();
  const [params, setParams] = useSearchParams();
  const { period, range } = readPeriod(params);
  const [report, setReport] = useState<ExpenseReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
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
  const choose = (next: Preset | Range) => {
    const preset = typeof next === 'string' ? next : presetOf(next);
    if (preset) setParams(preset === 'this-month' ? {} : { period: preset });
    else if (typeof next !== 'string') setParams({ period: 'custom', ...next });
  };

  async function download() {
    setExporting(true);
    try {
      const count = await exportCsv(range);
      toast(count === 1 ? 'Exported 1 bill' : `Exported ${count} bills`);
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setExporting(false);
    }
  }

  // The shown report may still be the previous period's while the new one loads.
  const shown = report && report.from === range.from && report.to === range.to ? report : null;

  return (
    <main className="mx-auto max-w-6xl px-4 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Expenses</h1>
          <p className="mt-1 text-slate-600">
            Where your money went, from the bills you saved · <span className="font-medium text-slate-800">{rangeLabel(range)}</span>
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <PeriodMenu period={period} range={range} onChoose={choose} />
          <Button icon={Download} busy={exporting} disabled={!shown || shown.bills === 0} onClick={download} title="Download the bills of this period as a CSV file">
            Export CSV
          </Button>
        </div>
      </div>

      <div className="mt-6">
        {error ? (
          <ErrorState title="Could not load your expenses" message={error} onRetry={load} />
        ) : !shown ? (
          <Skeleton />
        ) : (
          <div className="space-y-6">
            <SpendHero report={shown} onChanged={load} />
            {shown.bills === 0 && shown.categories.length === 0 ? (
              <EmptyPeriod report={shown} onChoose={choose} />
            ) : (
              <div className="grid gap-6 lg:grid-cols-2">
                <CategoryBreakdown report={shown} onChanged={load} />
                <Insights report={shown} />
                {shown.days.length > 0 && <DailyCalendar days={shown.days} />}
                <MonthTrend months={shown.months} range={range} onSelect={(month) => choose(monthRange(month))} />
                <WeekdayChart weekdays={shown.weekdays} />
                <RecurringPayments items={shown.recurring} />
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
