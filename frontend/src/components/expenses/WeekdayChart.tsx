import { CalendarDays } from 'lucide-react';
import { formatAmount } from '../../lib/documents';
import type { Weekday, WeekdaySpend } from '../../lib/expenses';
import { Panel, PanelEmpty } from '../dashboard/Panel';

export const WEEKDAY_NAMES: Record<Weekday, string> = {
  MONDAY: 'Monday',
  TUESDAY: 'Tuesday',
  WEDNESDAY: 'Wednesday',
  THURSDAY: 'Thursday',
  FRIDAY: 'Friday',
  SATURDAY: 'Saturday',
  SUNDAY: 'Sunday',
};

/**
 * Money per weekday in the period, as seven plain HTML bars. The busiest day is the dark
 * bar and is also named in words, so colour is never the only hint.
 */
export function WeekdayChart({ weekdays }: { weekdays: WeekdaySpend[] }) {
  const max = Math.max(0, ...weekdays.map((d) => d.amount));
  const busiest = weekdays.find((d) => d.amount === max && max > 0);

  return (
    <Panel icon={CalendarDays} title="By weekday">
      {!busiest ? (
        <PanelEmpty>No spending in this period.</PanelEmpty>
      ) : (
        <>
          <p className="text-sm text-slate-600">
            Most on <span className="font-semibold text-slate-900">{WEEKDAY_NAMES[busiest.day]}s</span> (
            {formatAmount(busiest.amount, { whole: true })})
          </p>
          <div className="mt-6 flex h-36 items-end gap-2 sm:gap-3">
            {weekdays.map((d) => {
              const name = WEEKDAY_NAMES[d.day];
              const bills = d.bills === 1 ? '1 bill' : `${d.bills} bills`;
              return (
                <div
                  key={d.day}
                  tabIndex={0}
                  aria-label={`${name}: ${formatAmount(d.amount)}, ${bills}`}
                  className="group relative flex h-full flex-1 flex-col items-center justify-end outline-none"
                >
                  <span className="pointer-events-none absolute -top-2 left-1/2 z-10 hidden -translate-x-1/2 -translate-y-full rounded-lg bg-slate-900 px-2.5 py-1.5 text-center text-xs whitespace-nowrap text-white shadow-lg group-hover:block group-focus-visible:block">
                    <span className="block font-medium">{name}</span>
                    {formatAmount(d.amount)} · {bills}
                  </span>
                  <span
                    className={`w-full rounded-t-md group-focus-visible:ring-2 group-focus-visible:ring-brand-100 ${
                      d.amount <= 0 ? 'bg-slate-100' : d === busiest ? 'bg-brand-700' : 'bg-brand-500/45 group-hover:bg-brand-500'
                    }`}
                    style={{ height: d.amount > 0 ? `max(${(d.amount / max) * 100}%, 4px)` : '4px' }}
                  />
                  <span className={`mt-1.5 text-[11px] ${d === busiest ? 'font-semibold text-slate-900' : 'text-slate-500'}`}>
                    {name.slice(0, 3)}
                  </span>
                </div>
              );
            })}
          </div>
        </>
      )}
    </Panel>
  );
}
