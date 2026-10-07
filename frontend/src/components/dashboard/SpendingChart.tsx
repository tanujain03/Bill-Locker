import { ChartColumn } from 'lucide-react';
import { Link } from 'react-router';
import { monthLink, type MonthSpend } from '../../lib/dashboard';
import { formatAmount, monthLabel } from '../../lib/documents';
import { Panel, PanelEmpty } from './Panel';

/**
 * Money spent per month, last 12 months, as plain HTML bars (no chart library).
 * Each bar shows its numbers on hover AND on keyboard focus, and opens that
 * month's saved bills.
 */
export function SpendingChart({ months, withoutDate }: { months: MonthSpend[]; withoutDate: number }) {
  const max = Math.max(0, ...months.map((m) => m.amount));
  const total = months.reduce((sum, m) => sum + m.amount, 0);

  return (
    <Panel icon={ChartColumn} title="Spending" hint="last 12 months" id="spending">
      {max === 0 ? (
        <PanelEmpty>No spending yet. Save a bill with a date and a total to see it here.</PanelEmpty>
      ) : (
        <>
          <p className="text-sm text-slate-600">
            <span className="text-2xl font-semibold text-slate-900 tabular-nums">{formatAmount(total)}</span> in 12 months
          </p>
          <div className="mt-6 flex h-44 items-end gap-1.5 sm:gap-2">
            {months.map((m, i) => {
              const label = monthLabel(m.month);
              const bills = m.bills === 1 ? '1 bill' : `${m.bills} bills`;
              const [year, monthNumber] = m.month.split('-');
              // Show the year under the first bar and under each January, so the axis reads clearly.
              const short = new Date(`${m.month}-01T00:00:00Z`).toLocaleDateString('en-IN', { month: 'short', timeZone: 'UTC' });
              return (
                <Link
                  key={m.month}
                  to={monthLink(m.month)}
                  aria-label={`${label}: ${formatAmount(m.amount)} from ${bills}`}
                  className="group relative flex h-full flex-1 flex-col items-center justify-end outline-none"
                >
                  {/* Tooltip: pure CSS, shown on hover and on keyboard focus. */}
                  <span className="pointer-events-none absolute -top-2 left-1/2 z-10 hidden -translate-x-1/2 -translate-y-full rounded-lg bg-slate-900 px-2.5 py-1.5 text-center text-xs whitespace-nowrap text-white shadow-lg group-hover:block group-focus-visible:block">
                    <span className="block font-medium">{label}</span>
                    {formatAmount(m.amount)} · {bills}
                  </span>
                  <span
                    className={`w-full rounded-t-md transition ${
                      m.amount > 0 ? 'bg-brand-500 group-hover:bg-brand-600' : 'bg-slate-100'
                    } group-focus-visible:ring-2 group-focus-visible:ring-brand-100`}
                    style={{ height: m.amount > 0 ? `max(${(m.amount / max) * 100}%, 4px)` : '4px' }}
                  />
                  <span className="mt-1.5 text-[11px] leading-tight text-slate-500">{short}</span>
                  <span className="text-[10px] leading-tight text-slate-400">
                    {i === 0 || monthNumber === '01' ? year : ' '}
                  </span>
                </Link>
              );
            })}
          </div>
        </>
      )}
      {withoutDate > 0 && (
        <p className="mt-3 text-xs text-slate-500">
          {withoutDate === 1 ? '1 saved bill has' : `${withoutDate} saved bills have`} no date or no total, so
          {withoutDate === 1 ? ' its' : ' their'} money isn’t on the chart.
        </p>
      )}
    </Panel>
  );
}
