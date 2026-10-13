import { CircleAlert, CircleCheck, Pencil, Target, TrendingDown, TrendingUp, X } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { ApiError, errorMessage } from '../../lib/api';
import { formatAmount } from '../../lib/documents';
import {
  change,
  monthsTouched,
  periodMonths,
  previousLabel,
  rangeLabel,
  setBudget,
  singleMonth,
  type ExpenseReport,
} from '../../lib/expenses';
import { useFeedback } from '../../lib/feedback-context';
import { Button } from '../Button';

/**
 * The big card at the top: the period's total, how it compares with the period before,
 * and the budget meter. Changing the budget calls onChanged so the page reloads the numbers.
 */
export function SpendHero({ report, onChanged }: { report: ExpenseReport; onChanged: () => void }) {
  const diff = change(report.total, report.previousTotal);
  const previous = previousLabel(report); // "Sept", "the 6 months before"…
  const bills = report.bills === 1 ? '1 bill' : `${report.bills} bills`;
  const months = monthsTouched(report);
  // The second number: the pace while the period runs; for several months the monthly
  // average; else what the period before cost.
  const second =
    report.projectedTotal !== null
      ? { label: 'At this pace', value: report.projectedTotal }
      : months > 1
        ? { label: 'Monthly average', value: report.total / months }
        : { label: previous.startsWith('the ') ? 'The period before' : `Before (${previous})`, value: report.previousTotal };

  return (
    <section className="grid gap-6 rounded-3xl bg-linear-to-br from-brand-600 to-brand-950 p-6 text-white shadow-lg shadow-brand-600/20 sm:p-8 lg:grid-cols-[1.2fr_1fr]">
      <div>
        <p className="text-sm font-medium text-white/80">Spent in {rangeLabel(report)}</p>
        <p className="mt-1 text-4xl font-semibold tracking-tight tabular-nums sm:text-5xl">
          {formatAmount(report.total, { whole: true })}
        </p>
        <p className="mt-3 flex flex-wrap items-center gap-2 text-sm text-white/85">
          {diff !== null ? (
            // Words carry the meaning; the arrow only repeats it.
            <span className="inline-flex items-center gap-1 rounded-full bg-white/15 px-2.5 py-1 font-medium">
              {diff >= 0 ? <TrendingUp className="size-4" aria-hidden /> : <TrendingDown className="size-4" aria-hidden />}
              {diff === 0
                ? `Same as ${previous}`
                : // "1255% more" says little; a big jump reads better in rupees.
                  `${diff >= 100 ? formatAmount(report.total - report.previousTotal, { whole: true }) : `${Math.abs(diff)}%`} ${
                    diff > 0 ? 'more' : 'less'
                  } than ${previous}`}
            </span>
          ) : (
            <span className="rounded-full bg-white/15 px-2.5 py-1 font-medium">Nothing in {previous} to compare</span>
          )}
          <span>{bills}</span>
        </p>
        <dl className="mt-6 grid grid-cols-2 gap-4 border-t border-white/15 pt-5 text-sm">
          <div>
            <dt className="text-white/70">Daily average</dt>
            <dd className="mt-0.5 text-lg font-semibold tabular-nums">{formatAmount(report.dailyAverage, { whole: true })}</dd>
          </div>
          <div>
            <dt className="text-white/70">{second.label}</dt>
            <dd className="mt-0.5 text-lg font-semibold tabular-nums">{formatAmount(second.value, { whole: true })}</dd>
          </div>
        </dl>
      </div>
      <BudgetMeter report={report} onChanged={onChanged} />
    </section>
  );
}

/**
 * The budget for the shown period: the monthly budget × the months it covers (6 for
 * "Last 6 months"). A custom range that doesn't start on the 1st counts its days instead.
 */
function periodBudget(report: ExpenseReport): { amount: number; months: number } | null {
  if (report.budget === null) return null;
  const months = periodMonths(report);
  return { amount: report.budget * months, months };
}

/** How much of the budget is used in the period, or a button to set one. */
function BudgetMeter({ report, onChanged }: { report: ExpenseReport; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const budget = periodBudget(report);

  if (editing || budget === null) {
    return editing ? (
      <BudgetForm current={report.budget} onDone={() => setEditing(false)} onChanged={onChanged} />
    ) : (
      <div className="flex flex-col items-start justify-center rounded-2xl bg-white/10 p-5 ring-1 ring-white/15">
        <Target className="size-6 text-white/90" aria-hidden />
        <p className="mt-3 font-semibold">Set a monthly budget</p>
        <p className="mt-1 text-sm text-white/75">
          See how much is left each month, and get a heads-up when your spending pace would go over.
        </p>
        <button
          type="button"
          onClick={() => setEditing(true)}
          className="mt-4 rounded-lg bg-white px-4 py-2 text-sm font-semibold text-brand-700 shadow-sm hover:bg-brand-50"
        >
          Set budget
        </button>
      </div>
    );
  }

  const used = report.total / budget.amount;
  const left = budget.amount - report.total;
  const over = left < 0;
  // Status: over (rose), close to it (amber), fine (emerald) — always with an icon and words.
  const state = over ? 'over' : used >= 0.8 ? 'close' : 'fine';
  const StateIcon = state === 'fine' ? CircleCheck : CircleAlert;
  const bar = { over: 'bg-rose-400', close: 'bg-amber-300', fine: 'bg-emerald-300' }[state];

  return (
    <div className="flex flex-col justify-center rounded-2xl bg-white/10 p-5 ring-1 ring-white/15">
      <div className="flex items-center justify-between gap-3">
        <p className="flex items-center gap-2 text-sm font-medium text-white/80">
          <Target className="size-4" aria-hidden />
          {budget.months === 1 || singleMonth(report)
            ? `Budget ${formatAmount(budget.amount, { whole: true })}`
            : `Budget ${formatAmount(report.budget!, { whole: true })} × ${budget.months} months`}
        </p>
        <button
          type="button"
          onClick={() => setEditing(true)}
          aria-label="Change budget"
          className="rounded-lg p-1.5 text-white/80 hover:bg-white/15 hover:text-white"
        >
          <Pencil className="size-4" aria-hidden />
        </button>
      </div>
      <p className="mt-3 text-2xl font-semibold tabular-nums">
        {over ? `${formatAmount(-left, { whole: true })} over` : `${formatAmount(left, { whole: true })} left`}
      </p>
      <div
        className="mt-3 h-3 overflow-hidden rounded-full bg-white/15"
        role="meter"
        aria-label="Budget used"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(used * 100)}
      >
        <span className={`block h-full rounded-full ${bar}`} style={{ width: `${Math.min(used, 1) * 100}%` }} />
      </div>
      <p className="mt-3 flex items-start gap-1.5 text-sm text-white/85">
        <StateIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
        {budgetLine(report, budget.amount, used)}
      </p>
    </div>
  );
}

function budgetLine(report: ExpenseReport, budget: number, used: number): string {
  const percent = `${Math.round(used * 100)}% used`;
  if (report.total > budget) return `${percent}. Over budget for this period.`;
  if (report.projectedTotal === null) return percent; // a finished period
  if (report.projectedTotal > budget) {
    return `${percent}. At this pace you’ll go over by ${formatAmount(report.projectedTotal - budget, { whole: true })}.`;
  }
  return `${percent}. On track.`;
}

/** Type a budget, save it, or remove it. The backend checks the amount. */
function BudgetForm({ current, onDone, onChanged }: { current: number | null; onDone: () => void; onChanged: () => void }) {
  const { toast } = useFeedback();
  const [value, setValue] = useState(current === null ? '' : String(current));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save(amount: number | null) {
    setBusy(true);
    setError(null);
    try {
      await setBudget(amount);
      toast(amount === null ? 'Budget removed' : 'Budget saved');
      onDone();
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? (e.fieldErrors.amount ?? e.message) : errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    save(value.trim() === '' ? null : Number(value));
  }

  return (
    <form
      onSubmit={submit}
      noValidate
      className="flex flex-col justify-center rounded-2xl bg-white p-5 text-slate-900 shadow-sm"
    >
      <div className="flex items-center justify-between">
        <label htmlFor="budget" className="text-sm font-semibold">
          Monthly budget
        </label>
        <button type="button" onClick={onDone} aria-label="Cancel" className="rounded-lg p-1 text-slate-500 hover:bg-slate-100">
          <X className="size-4" aria-hidden />
        </button>
      </div>
      <div className="relative mt-2">
        <span className="absolute top-1/2 left-3 -translate-y-1/2 text-slate-500">₹</span>
        <input
          id="budget"
          type="number"
          inputMode="decimal"
          autoFocus
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="25000"
          aria-invalid={Boolean(error)}
          aria-describedby={error ? 'budget-error' : undefined}
          className="block w-full rounded-lg border border-slate-300 py-2 pr-3 pl-7 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
        />
      </div>
      {error && (
        <p id="budget-error" className="mt-1.5 text-sm text-rose-700">
          {error}
        </p>
      )}
      <div className="mt-4 flex flex-wrap gap-2">
        <Button type="submit" variant="primary" size="sm" busy={busy}>
          Save
        </Button>
        {current !== null && (
          <Button variant="dangerGhost" size="sm" disabled={busy} onClick={() => save(null)}>
            Remove budget
          </Button>
        )}
      </div>
    </form>
  );
}
