import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

type Props = {
  icon: LucideIcon;
  label: string;
  value: ReactNode;
  /** A short line under the number, e.g. "+4 this month". */
  sub?: string;
  /** Where the whole card leads, or a click handler (e.g. scroll to the chart). */
  to?: string;
  onClick?: () => void;
  /** Amber when something waits for the user. */
  attention?: boolean;
};

/** One of the four numbers at the top of the dashboard. The whole card is clickable. */
export function StatCard({ icon: Icon, label, value, sub, to, onClick, attention }: Props) {
  const className = `group flex w-full items-start gap-4 rounded-2xl border bg-white p-5 text-left shadow-xs transition hover:-translate-y-0.5 hover:shadow-md ${
    attention ? 'border-amber-300 ring-1 ring-amber-100' : 'border-slate-200'
  }`;
  const body = (
    <>
      <span
        className={`grid size-11 shrink-0 place-items-center rounded-xl ${
          attention ? 'bg-amber-50 text-amber-600' : 'bg-brand-50 text-brand-600'
        }`}
      >
        <Icon className="size-5" aria-hidden />
      </span>
      <span className="min-w-0">
        <span className="block text-sm font-medium text-slate-500">{label}</span>
        <span className="mt-0.5 block truncate text-2xl font-semibold tracking-tight tabular-nums">{value}</span>
        {sub && <span className="mt-0.5 block truncate text-xs text-slate-500">{sub}</span>}
      </span>
    </>
  );
  return to ? (
    <Link to={to} className={className}>
      {body}
    </Link>
  ) : (
    <button type="button" onClick={onClick} className={className}>
      {body}
    </button>
  );
}
