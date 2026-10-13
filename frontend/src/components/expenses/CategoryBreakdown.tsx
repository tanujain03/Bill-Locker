import { ArrowRight, ChartPie, CircleAlert, CircleCheck, Pencil, Plus, X } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ApiError, errorMessage } from '../../lib/api';
import { BILL_CATEGORY_LABELS, formatAmount, type BillCategory } from '../../lib/documents';
import {
  categoryIcon,
  change,
  expenseLink,
  periodMonths,
  previousLabel,
  setCategoryBudget,
  type CategorySpend,
  type ExpenseReport,
} from '../../lib/expenses';
import { useFeedback } from '../../lib/feedback-context';
import { Button } from '../Button';
import { Panel, PanelEmpty } from '../dashboard/Panel';

/**
 * Where the money went, per category: one bar per category (one colour, the icon and label
 * say which), its share of the period and the change against the period before. A category
 * can have a monthly limit: its bar then shows how much of the limit is used. Each row opens
 * that category's bills of the period.
 */
export function CategoryBreakdown({ report, onChanged }: { report: ExpenseReport; onChanged: () => void }) {
  const [editing, setEditing] = useState<BillCategory | 'NEW' | null>(null);
  const max = Math.max(0, ...report.categories.map((c) => c.amount));
  const previous = previousLabel(report);
  const months = periodMonths(report);
  const withoutLimit = (Object.keys(BILL_CATEGORY_LABELS) as BillCategory[]).filter(
    (c) => !report.categories.some((s) => s.category === c && s.budget !== null),
  );

  return (
    <Panel icon={ChartPie} title="By category">
      {report.categories.length === 0 ? (
        <PanelEmpty>No saved bills in this period.</PanelEmpty>
      ) : (
        <ul className="-mx-2 space-y-1">
          {report.categories.map((c) => (
            <li key={c.category ?? 'NONE'}>
              <CategoryRow
                c={c}
                report={report}
                max={max}
                months={months}
                previous={previous}
                onEdit={() => setEditing(c.category)}
              />
              {editing !== null && editing === c.category && c.category && (
                <LimitForm
                  category={c.category}
                  current={c.budget}
                  onDone={() => setEditing(null)}
                  onChanged={onChanged}
                />
              )}
            </li>
          ))}
        </ul>
      )}

      {report.uncategorizedBills > 0 && (
        <Link
          to={expenseLink(report, null)}
          className="mt-3 flex items-center justify-between gap-3 rounded-xl bg-amber-50 px-3 py-2.5 text-sm text-amber-900 hover:bg-amber-100"
        >
          <span>
            {report.uncategorizedBills === 1 ? '1 bill has' : `${report.uncategorizedBills} bills have`} no category yet. Open
            {report.uncategorizedBills === 1 ? ' it' : ' them'} and pick one for a clearer picture.
          </span>
          <ArrowRight className="size-4 shrink-0" aria-hidden />
        </Link>
      )}

      {editing === 'NEW' ? (
        <LimitForm choices={withoutLimit} current={null} onDone={() => setEditing(null)} onChanged={onChanged} />
      ) : (
        withoutLimit.length > 0 && (
          <button
            type="button"
            onClick={() => setEditing('NEW')}
            className="mt-3 inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm font-medium text-brand-700 hover:bg-brand-50"
          >
            <Plus className="size-4" aria-hidden />
            Add a monthly limit for a category
          </button>
        )
      )}
    </Panel>
  );
}

function CategoryRow(props: {
  c: CategorySpend;
  report: ExpenseReport;
  max: number;
  months: number;
  previous: string;
  onEdit: () => void;
}) {
  const { c, report, max, months, previous } = props;
  const Icon = categoryIcon(c.category);
  const share = report.total > 0 ? Math.round((c.amount / report.total) * 100) : 0;
  const diff = change(c.amount, c.previousAmount);
  const label = c.category ? BILL_CATEGORY_LABELS[c.category] : 'Not sorted yet';
  // The limit is per month; a 6-month period gets 6 × the limit.
  const limit = c.budget === null ? null : c.budget * months;
  const used = limit ? c.amount / limit : 0;
  const state = limit === null ? null : used > 1 ? 'over' : used >= 0.8 ? 'close' : 'fine';
  const barColour = !c.category ? 'bg-amber-400' : state === 'over' ? 'bg-rose-500' : state === 'close' ? 'bg-amber-400' : state === 'fine' ? 'bg-emerald-500' : 'bg-brand-500';
  const width = limit ? Math.min(used, 1) * 100 : max ? Math.max((c.amount / max) * 100, c.amount > 0 ? 2 : 0) : 0;

  return (
    <div className="flex items-center gap-1 rounded-xl hover:bg-slate-50">
      <Link to={expenseLink(report, c.category)} className="flex min-w-0 flex-1 items-center gap-3 px-2 py-2.5">
        <span
          className={`grid size-10 shrink-0 place-items-center rounded-xl ${
            c.category ? 'bg-brand-50 text-brand-600' : 'bg-amber-50 text-amber-600'
          }`}
        >
          <Icon className="size-5" aria-hidden />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex items-baseline justify-between gap-3 text-sm">
            <span className="truncate font-medium">{label}</span>
            <span className="shrink-0 font-semibold tabular-nums">
              {formatAmount(c.amount)}
              {limit !== null && <span className="font-normal text-slate-500"> / {formatAmount(limit, { whole: true })}</span>}
            </span>
          </span>
          <span className="mt-1.5 flex items-center gap-2">
            <span className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
              <span className={`block h-full rounded-full ${barColour}`} style={{ width: `${width}%` }} />
            </span>
            <span className="w-10 shrink-0 text-right text-xs font-medium text-slate-700 tabular-nums">
              {limit !== null ? `${Math.round(used * 100)}%` : `${share}%`}
            </span>
          </span>
          <span className="mt-1 flex flex-wrap items-center gap-x-1 text-xs text-slate-500">
            {state === 'over' && (
              <span className="inline-flex items-center gap-1 font-medium text-rose-700">
                <CircleAlert className="size-3.5" aria-hidden />
                Over limit by {formatAmount(c.amount - (limit ?? 0), { whole: true })} ·
              </span>
            )}
            {state === 'close' && (
              <span className="inline-flex items-center gap-1 font-medium text-amber-700">
                <CircleAlert className="size-3.5" aria-hidden />
                Close to limit ·
              </span>
            )}
            {state === 'fine' && (
              <span className="inline-flex items-center gap-1 text-emerald-700">
                <CircleCheck className="size-3.5" aria-hidden />
                Within limit ·
              </span>
            )}
            <span>
              {c.bills === 1 ? '1 bill' : `${c.bills} bills`}
              {limit !== null && ` · ${share}% of spending`}
              {diff !== null && diff !== 0 && ` · ${diff > 0 ? '▲' : '▼'} ${Math.abs(diff)}% vs ${previous}`}
              {!c.category && ' · tap to sort them'}
            </span>
          </span>
        </span>
      </Link>
      {c.category && (
        <button
          type="button"
          onClick={props.onEdit}
          aria-label={c.budget === null ? `Set a monthly limit for ${label}` : `Change the monthly limit for ${label}`}
          title={c.budget === null ? 'Set a monthly limit' : 'Change the monthly limit'}
          className="mr-1 shrink-0 rounded-lg p-2 text-slate-400 hover:bg-white hover:text-slate-700"
        >
          <Pencil className="size-4" aria-hidden />
        </button>
      )}
    </div>
  );
}

/** Set, change or remove one category's monthly limit. With `choices`, the category is picked first. */
function LimitForm(props: {
  category?: BillCategory;
  choices?: BillCategory[];
  current: number | null;
  onDone: () => void;
  onChanged: () => void;
}) {
  const { toast } = useFeedback();
  const [category, setCategory] = useState<BillCategory | ''>(props.category ?? '');
  const [value, setValue] = useState(props.current === null ? '' : String(props.current));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save(amount: number | null) {
    if (!category) {
      setError('Pick a category.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await setCategoryBudget(category, amount);
      toast(amount === null ? 'Limit removed' : `Limit saved for ${BILL_CATEGORY_LABELS[category]}`);
      props.onDone();
      props.onChanged();
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
    <form onSubmit={submit} noValidate className="mx-2 mt-1 mb-3 rounded-xl border border-slate-200 bg-slate-50 p-3">
      <div className="flex items-center justify-between">
        <p className="text-sm font-semibold">
          Monthly limit{props.category ? ` · ${BILL_CATEGORY_LABELS[props.category]}` : ''}
        </p>
        <button type="button" onClick={props.onDone} aria-label="Cancel" className="rounded-lg p-1 text-slate-500 hover:bg-white">
          <X className="size-4" aria-hidden />
        </button>
      </div>
      <div className="mt-2 flex flex-wrap items-start gap-2">
        {props.choices && (
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as BillCategory)}
            aria-label="Category"
            className="rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
          >
            <option value="">Category…</option>
            {props.choices.map((c) => (
              <option key={c} value={c}>
                {BILL_CATEGORY_LABELS[c]}
              </option>
            ))}
          </select>
        )}
        <div className="relative w-36">
          <span className="absolute top-1/2 left-3 -translate-y-1/2 text-sm text-slate-500">₹</span>
          <input
            type="number"
            inputMode="decimal"
            autoFocus={!props.choices}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="6000"
            aria-label="Limit per month in rupees"
            aria-invalid={Boolean(error)}
            className="block w-full rounded-lg border border-slate-300 bg-white py-2 pr-2 pl-7 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
          />
        </div>
        <Button type="submit" variant="primary" size="sm" busy={busy} className="py-2">
          Save
        </Button>
        {props.current !== null && (
          <Button variant="dangerGhost" size="sm" disabled={busy} onClick={() => save(null)} className="py-2">
            Remove
          </Button>
        )}
      </div>
      {error && <p className="mt-1.5 text-sm text-rose-700">{error}</p>}
    </form>
  );
}
