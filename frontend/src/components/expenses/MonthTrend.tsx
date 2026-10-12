import { ChartColumn } from 'lucide-react';
import { formatAmount, monthLabel } from '../../lib/documents';
import type { MonthSpend, Range } from '../../lib/expenses';
import { Panel } from '../dashboard/Panel';

/**
 * Spending per month as plain HTML bars (12 or more). The months of the shown period are
 * the dark bars; clicking a bar switches the whole page to that one month.
 */
export function MonthTrend({ months, range, onSelect }: { months: MonthSpend[]; range: Range; onSelect: (month: string) => void }) {
  const max = Math.max(0, ...months.map((m) => m.amount));
  const withSpend = months.filter((m) => m.amount > 0);
  const average = withSpend.length ? withSpend.reduce((s, m) => s + m.amount, 0) / withSpend.length : 0;

  return (
    <Panel icon={ChartColumn} title="Month by month" hint={`${months.length} months`}>
      <p className="text-sm text-slate-600">
        Average <span className="font-semibold text-slate-900 tabular-nums">{formatAmount(average, { whole: true })}</span> a
        month with spending
      </p>
      <div className="mt-6 flex h-40 items-end gap-1.5 sm:gap-2">
        {months.map((m) => {
          const label = monthLabel(m.month);
          const isSelected = m.month >= range.from.slice(0, 7) && m.month <= range.to.slice(0, 7);
          const short = label.split(' ')[0];
          return (
            <button
              key={m.month}
              type="button"
              onClick={() => onSelect(m.month)}
              aria-pressed={isSelected}
              aria-label={`${label}: ${formatAmount(m.amount)}, ${m.bills === 1 ? '1 bill' : `${m.bills} bills`}`}
              className="group relative flex h-full flex-1 flex-col items-center justify-end outline-none"
            >
              <span className="pointer-events-none absolute -top-2 left-1/2 z-10 hidden -translate-x-1/2 -translate-y-full rounded-lg bg-slate-900 px-2.5 py-1.5 text-center text-xs whitespace-nowrap text-white shadow-lg group-hover:block group-focus-visible:block">
                <span className="block font-medium">{label}</span>
                {formatAmount(m.amount)}
              </span>
              <span
                className={`w-full rounded-t-md transition group-focus-visible:ring-2 group-focus-visible:ring-brand-100 ${
                  m.amount <= 0 ? 'bg-slate-100' : isSelected ? 'bg-brand-700' : 'bg-brand-500/45 group-hover:bg-brand-500'
                }`}
                style={{ height: m.amount > 0 ? `max(${(m.amount / max) * 100}%, 4px)` : '4px' }}
              />
              <span className={`mt-1.5 text-[11px] leading-tight ${isSelected ? 'font-semibold text-slate-900' : 'text-slate-500'}`}>
                {short}
              </span>
            </button>
          );
        })}
      </div>
    </Panel>
  );
}
