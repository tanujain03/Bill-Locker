import { CalendarDays, CircleAlert, Crown, Lightbulb, PiggyBank, Repeat, TrendingDown, TrendingUp, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router';
import { BILL_CATEGORY_LABELS, formatAmount } from '../../lib/documents';
import { change, daysBetween, expenseLink, periodMonths, previousLabel, singleMonth, today, type ExpenseReport } from '../../lib/expenses';
import { WEEKDAY_NAMES } from './WeekdayChart';
import { Panel } from '../dashboard/Panel';

type Insight = { icon: LucideIcon; text: string; to?: string };

/**
 * A few plain sentences about the period, worked out from the report: what changed,
 * what took the most, how the budget and the limits are going. At most five, the most useful first.
 */
export function Insights({ report }: { report: ExpenseReport }) {
  const insights = buildInsights(report).slice(0, 5);
  if (insights.length === 0) return null;
  return (
    <Panel icon={Lightbulb} title="Insights">
      <ul className="space-y-2">
        {insights.map((insight) => {
          const Icon = insight.icon;
          const body = (
            <>
              <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-600">
                <Icon className="size-4" aria-hidden />
              </span>
              <span className="pt-1">{insight.text}</span>
            </>
          );
          return (
            <li key={insight.text}>
              {insight.to ? (
                <Link to={insight.to} className="flex gap-3 rounded-xl p-2 text-sm text-slate-700 hover:bg-slate-50">
                  {body}
                </Link>
              ) : (
                <div className="flex gap-3 p-2 text-sm text-slate-700">{body}</div>
              )}
            </li>
          );
        })}
      </ul>
    </Panel>
  );
}

function buildInsights(r: ExpenseReport): Insight[] {
  const list: Insight[] = [];
  const previous = previousLabel(r);
  const money = (n: number) => formatAmount(n, { whole: true });

  // Budget pace first: it is the one you can still act on (only for a month that is running).
  if (r.budget !== null && r.projectedTotal !== null && singleMonth(r)) {
    const daysLeft = daysBetween(today(), r.to) - 1; // after today
    const left = r.budget - r.total;
    if (left < 0) {
      list.push({ icon: TrendingUp, text: `You’re ${money(-left)} over your monthly budget.` });
    } else if (r.projectedTotal > r.budget) {
      list.push({
        icon: TrendingUp,
        text: `At this pace you’ll spend ${money(r.projectedTotal)}, ${money(r.projectedTotal - r.budget)} over budget. Keep to ${money(left / Math.max(daysLeft, 1))} a day to stay within it.`,
      });
    } else if (daysLeft > 0) {
      list.push({ icon: PiggyBank, text: `On track: ${money(left)} left, about ${money(left / daysLeft)} a day for the next ${daysLeft} days.` });
    }
  }

  // A category over its monthly limit (the one furthest over).
  const months = periodMonths(r);
  const over = r.categories
    .filter((c) => c.category && c.budget !== null && c.amount > c.budget * months)
    .sort((a, b) => b.amount - b.budget! * months - (a.amount - a.budget! * months))[0];
  if (over) {
    list.push({
      icon: CircleAlert,
      text: `${BILL_CATEGORY_LABELS[over.category!]} is over its limit by ${money(over.amount - over.budget! * months)} (${money(over.amount)} of ${money(over.budget! * months)}).`,
      to: expenseLink(r, over.category),
    });
  }

  const top = r.categories.find((c) => c.category && c.amount > 0);
  if (top && r.total > 0) {
    const share = Math.round((top.amount / r.total) * 100);
    list.push({
      icon: Crown,
      text: `${BILL_CATEGORY_LABELS[top.category!]} took ${share}% of your spending (${money(top.amount)}).`,
    });
  }

  // The category that grew the most against last month (only a real rise: +20% and ₹100).
  const rise = r.categories
    .filter((c) => c.category && c.previousAmount > 0 && c.amount - c.previousAmount >= 100)
    .map((c) => ({ c, diff: change(c.amount, c.previousAmount) ?? 0 }))
    .filter((x) => x.diff >= 20)
    .sort((a, b) => b.c.amount - b.c.previousAmount - (a.c.amount - a.c.previousAmount))[0];
  if (rise) {
    list.push({
      icon: TrendingUp,
      text: `${BILL_CATEGORY_LABELS[rise.c.category!]} is up ${rise.diff}% from ${previous} (${money(rise.c.previousAmount)} → ${money(rise.c.amount)}).`,
    });
  }

  if (r.recurring.length > 0) {
    const monthly = r.recurring.reduce((sum, x) => sum + x.averageAmount, 0);
    list.push({
      icon: Repeat,
      text: `${r.recurring.length === 1 ? '1 recurring payment' : `${r.recurring.length} recurring payments`} cost about ${money(monthly)} a month, the biggest is ${r.recurring[0].name}.`,
    });
  }

  const diff = change(r.total, r.previousTotal);
  if (diff !== null && diff < 0) {
    list.push({ icon: TrendingDown, text: `Nice: you spent ${money(r.previousTotal - r.total)} less than in ${previous}.` });
  }

  // The weekday pattern only means something with a few bills.
  const weekday = r.weekdays.reduce((best, d) => (d.amount > best.amount ? d : best), r.weekdays[0]);
  if (weekday && r.bills >= 3 && weekday.amount > 0) {
    list.push({ icon: CalendarDays, text: `You spend the most on ${WEEKDAY_NAMES[weekday.day]}s: ${money(weekday.amount)} in this period.` });
  }

  const busiest = r.days.reduce((best, d) => (d.amount > best.amount ? d : best), r.days[0]);
  if (busiest && busiest.amount > 0 && busiest.bills > 0) {
    const day = new Date(`${busiest.date}T00:00:00Z`).toLocaleDateString('en-IN', {
      weekday: 'long',
      day: 'numeric',
      month: 'short',
      timeZone: 'UTC',
    });
    list.push({ icon: CalendarDays, text: `Your biggest spending day was ${day}: ${money(busiest.amount)}.` });
  }

  if (r.biggestBill?.totalAmount) {
    list.push({
      icon: Crown,
      text: `Biggest bill: ${r.biggestBill.sellerName ?? r.biggestBill.fileName}, ${money(r.biggestBill.totalAmount)}.`,
      to: `/documents/${r.biggestBill.id}`,
    });
  }
  return list;
}
