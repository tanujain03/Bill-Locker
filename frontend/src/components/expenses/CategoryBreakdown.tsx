import { ArrowRight, ChartPie } from 'lucide-react';
import { Link } from 'react-router';
import { BILL_CATEGORY_LABELS, formatAmount } from '../../lib/documents';
import { categoryIcon, change, expenseLink, previousLabel, type ExpenseReport } from '../../lib/expenses';
import { Panel, PanelEmpty } from '../dashboard/Panel';

/**
 * Where the money went, per category: one bar per category (one colour, the icon and
 * label say which), its share of the period, and the change against the period before.
 * Each row opens that category's bills of the period.
 */
export function CategoryBreakdown({ report }: { report: ExpenseReport }) {
  const max = Math.max(0, ...report.categories.map((c) => c.amount));
  const previous = previousLabel(report);
  return (
    <Panel icon={ChartPie} title="By category">
      {report.categories.length === 0 ? (
        <PanelEmpty>No saved bills in this period.</PanelEmpty>
      ) : (
        <ul className="-mx-2 space-y-1">
          {report.categories.map((c) => {
            const Icon = categoryIcon(c.category);
            const share = report.total > 0 ? Math.round((c.amount / report.total) * 100) : 0;
            const diff = change(c.amount, c.previousAmount);
            const label = c.category ? BILL_CATEGORY_LABELS[c.category] : 'Not sorted yet';
            return (
              <li key={c.category ?? 'NONE'}>
                <Link
                  to={expenseLink(report, c.category)}
                  className="flex items-center gap-3 rounded-xl px-2 py-2.5 hover:bg-slate-50"
                >
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
                      <span className="shrink-0 font-semibold tabular-nums">{formatAmount(c.amount)}</span>
                    </span>
                    <span className="mt-1.5 flex items-center gap-2">
                      <span className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                        <span
                          className={`block h-full rounded-full ${c.category ? 'bg-brand-500' : 'bg-amber-400'}`}
                          style={{ width: `${max ? Math.max((c.amount / max) * 100, 2) : 0}%` }}
                        />
                      </span>
                      <span className="w-10 shrink-0 text-right text-xs font-medium text-slate-700 tabular-nums">{share}%</span>
                    </span>
                    <span className="mt-1 block text-xs text-slate-500">
                      {c.bills === 1 ? '1 bill' : `${c.bills} bills`}
                      {diff !== null && diff !== 0 && ` · ${diff > 0 ? '▲' : '▼'} ${Math.abs(diff)}% vs ${previous}`}
                      {!c.category && ' · tap to sort them'}
                    </span>
                  </span>
                </Link>
              </li>
            );
          })}
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
    </Panel>
  );
}
