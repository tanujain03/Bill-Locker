import { Repeat } from 'lucide-react';
import { Link } from 'react-router';
import { BILL_CATEGORY_LABELS, formatAmount } from '../../lib/documents';
import { categoryIcon, today, type RecurringSpend } from '../../lib/expenses';
import { Panel, PanelEmpty } from '../dashboard/Panel';

const shortDate = (iso: string) =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', timeZone: 'UTC' });

/**
 * Payments that come back every month (phone, electricity, subscriptions), found by the
 * backend in the 6 months up to the period's end. The next date is an estimate: one month
 * after the last payment.
 */
export function RecurringPayments({ items }: { items: RecurringSpend[] }) {
  const monthly = items.reduce((sum, r) => sum + r.averageAmount, 0);
  const now = today();

  return (
    <Panel icon={Repeat} title="Recurring payments" hint="last 6 months">
      {items.length === 0 ? (
        <PanelEmpty>
          Nothing recurring yet. A shop you pay in two or more months with a similar amount (phone, electricity,
          subscriptions) shows up here.
        </PanelEmpty>
      ) : (
        <>
          <p className="text-sm text-slate-600">
            About <span className="font-semibold text-slate-900 tabular-nums">{formatAmount(monthly, { whole: true })}</span> a
            month in {items.length === 1 ? '1 recurring payment' : `${items.length} recurring payments`}
          </p>
          <ul className="-mx-2 mt-3 divide-y divide-slate-100">
            {items.map((r) => {
              const Icon = categoryIcon(r.category);
              const due = r.nextDate >= now;
              return (
                <li key={r.name}>
                  <Link
                    to={`/documents?status=SAVED&q=${encodeURIComponent(r.name)}`}
                    className="flex items-center gap-3 rounded-lg px-2 py-2.5 hover:bg-slate-50"
                  >
                    <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-600">
                      <Icon className="size-4" aria-hidden />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{r.name}</span>
                      <span className="block truncate text-xs text-slate-500">
                        {[r.category ? BILL_CATEGORY_LABELS[r.category] : null, `paid in ${r.months} months`]
                          .filter(Boolean)
                          .join(' · ')}
                      </span>
                    </span>
                    <span className="shrink-0 text-right">
                      <span className="block text-sm font-semibold tabular-nums">{formatAmount(r.averageAmount, { whole: true })}/mo</span>
                      <span className={`block text-xs ${due ? 'text-slate-500' : 'text-amber-700'}`}>
                        {due ? `next ~${shortDate(r.nextDate)}` : `was due ~${shortDate(r.nextDate)}`}
                      </span>
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </Panel>
  );
}
