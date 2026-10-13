import { CalendarDays } from 'lucide-react';
import { formatAmount } from '../../lib/documents';
import { today as todayIso, type DaySpend } from '../../lib/expenses';
import { Panel } from '../dashboard/Panel';

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
/** Five shades of one colour (the brand indigo), light = little, dark = a lot. */
const SHADES = [0.12, 0.3, 0.5, 0.72, 1];

/**
 * The period as a calendar (periods up to 62 days): each day is shaded by how much was
 * spent that day, so busy days stand out at a glance. Hover or focus a day for its amount.
 */
export function DailyCalendar({ days }: { days: DaySpend[] }) {
  const max = Math.max(0, ...days.map((d) => d.amount));
  const today = todayIso();
  // Monday-first: empty cells before the 1st so it lands under its weekday.
  const blanks = (new Date(`${days[0].date}T00:00:00Z`).getUTCDay() + 6) % 7;

  return (
    <Panel icon={CalendarDays} title="Day by day">
      <div className="grid grid-cols-7 gap-1.5 text-center">
        {WEEKDAYS.map((w) => (
          <span key={w} className="pb-1 text-[11px] font-medium text-slate-500">
            {w}
          </span>
        ))}
        {Array.from({ length: blanks }, (_, i) => (
          <span key={`blank-${i}`} />
        ))}
        {days.map((d) => {
          const level = d.amount > 0 && max > 0 ? Math.min(Math.ceil((d.amount / max) * SHADES.length), SHADES.length) - 1 : -1;
          const dayNumber = Number(d.date.slice(8));
          const label = `${dayNumber} ${new Date(`${d.date}T00:00:00Z`).toLocaleDateString('en-IN', { month: 'short', timeZone: 'UTC' })}`;
          const detail = d.amount > 0 ? `${formatAmount(d.amount)} · ${d.bills === 1 ? '1 bill' : `${d.bills} bills`}` : 'No spending';
          return (
            <span
              key={d.date}
              tabIndex={0}
              aria-label={`${label}: ${detail}`}
              className={`group relative grid aspect-square place-items-center rounded-lg text-xs tabular-nums outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
                level >= 3 ? 'font-semibold text-white' : 'text-slate-700'
              } ${level < 0 ? 'bg-slate-50' : ''} ${d.date === today ? 'ring-2 ring-slate-900/70' : ''} ${
                d.date > today ? 'opacity-40' : ''
              }`}
              style={level >= 0 ? { backgroundColor: `rgb(79 70 229 / ${SHADES[level]})` } : undefined}
            >
              {dayNumber}
              <span className="pointer-events-none absolute -top-1 left-1/2 z-10 hidden -translate-x-1/2 -translate-y-full rounded-lg bg-slate-900 px-2.5 py-1.5 text-xs font-normal whitespace-nowrap text-white shadow-lg group-hover:block group-focus-visible:block">
                <span className="block font-medium">{label}</span>
                {detail}
              </span>
            </span>
          );
        })}
      </div>
      {/* The key: what the shades mean. */}
      <div className="mt-4 flex items-center justify-end gap-1.5 text-[11px] text-slate-500">
        Less
        {SHADES.map((s) => (
          <span key={s} className="size-3 rounded" style={{ backgroundColor: `rgb(79 70 229 / ${s})` }} />
        ))}
        More
      </div>
    </Panel>
  );
}
